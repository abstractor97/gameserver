package com.game.module.copy;

import com.game.module.attach.catchgold.CatchGoldAttach;
import com.game.module.attach.catchgold.CatchGoldLogic;
import com.game.module.attach.leadaway.LeadAwayAttach;
import com.game.module.attach.leadaway.LeadAwayLogic;
import com.game.module.player.PlayerData;
import com.game.params.copy.*;
import org.springframework.beans.factory.annotation.Autowired;

import com.game.data.CopyConfig;
import com.game.data.Response;
import com.game.module.attach.endless.EndlessLogic;
import com.game.module.attach.experience.ExperienceAttach;
import com.game.module.attach.experience.ExperienceLogic;
import com.game.module.attach.treasure.TreasureAttach;
import com.game.module.attach.treasure.TreasureLogic;
import com.game.module.daily.DailyService;
import com.game.module.friend.FriendService;
import com.game.module.player.Player;
import com.game.module.player.PlayerService;
import com.game.module.scene.SceneService;
import com.game.module.shop.ShopService;
import com.game.module.team.Team;
import com.game.module.team.TeamService;
import com.game.params.CopyReward;
import com.game.params.EndlessInfo;
import com.game.params.Int2Param;
import com.game.params.IntParam;
import com.game.params.ListParam;
import com.game.params.Reward;
import com.game.params.scene.CMonster;
import com.game.util.ConfigData;
import com.server.anotation.Command;
import com.server.anotation.Extension;
import com.server.util.ServerLogger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Extension
public class CopyExtension {

    @Autowired
    private CopyService copyService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private FriendService friendService;
    @Autowired
    private SceneService sceneService;
    @Autowired
    private DailyService dailyService;
    @Autowired
    private ShopService shopService;
    @Autowired
    private EndlessLogic endlessLogic;
    @Autowired
    private TreasureLogic treasureLogic;
    @Autowired
    private ExperienceLogic experienceLogic;
    @Autowired
    private TeamService teamService;
    @Autowired
    private LeadAwayLogic leadAwayLogic;
    @Autowired
    private CatchGoldLogic catchGoldLogic;

    // 获取副本信息
    @Command(1901)
    public Object getInfo(int playerId, Object param) {
        return copyService.getCopys(playerId);
    }

    // 进入副本
    public static final int ENTER_COPY = 1902;

    @Command(1902)
    public Object enter(int playerId, IntParam param) {
        int copyId = param.param;
        Player player = playerService.getPlayer(playerId);
        SEnterCopy returnResult = new SEnterCopy();
        if (player.getTeamId() > 0) {
            Team team = teamService.getTeam(player.getTeamId());
            if (team.getCopyId() != copyId) {
                returnResult.code = Response.TEAM_NO_EXIT;
                return returnResult;
            }
        }

        int result = copyService.enter(playerId, copyId);


        int copyInstanceId = player.getCopyId();

        CopyInstance instance = copyService.getCopyInstance(copyInstanceId);

        returnResult.code = result;

        if (instance != null) {
            instance.getMembers().getAndIncrement();
            CopyConfig cfg = ConfigData.getConfig(CopyConfig.class, instance.getPassId());
            returnResult.sceneId = cfg.scenes[0];// 第一个场景id
            returnResult.copyId = instance.getCopyId();
            returnResult.passId = instance.getPassId();
        }

        copyService.updateRecord(playerId, returnResult);//更新世界记录和自身记录

        //置空统计伤害
        PlayerData playerData = playerService.getPlayerData(playerId);
        if (playerData == null) {
            ServerLogger.warn("玩家数据不存在=" + playerId);
        } else {
            playerData.setHurt(0);
            returnResult.customPara = playerData.getSingleAndMulti();
            playerData.setSingleAndMulti(0);
        }

        return returnResult;
    }

    // 领取奖励
    public static final int TAKE_COPY_REWARDS = 1903;

    @Command(1903)
    public Object getRewards(int playerId, CopyResult result) {
        Player player = playerService.getPlayer(playerId);
        int copyInstanceId = player.getCopyId();
        if (copyInstanceId == 0) {
            ServerLogger.info("Err CopyId:", copyInstanceId);
            result.victory = false;
            return result;
        }
        CopyInstance instance = copyService.getCopyInstance(copyInstanceId);
        int copyId = instance.getCopyId();

        PlayerData playerData = playerService.getPlayerData(playerId);
        if (playerData == null) {
            ServerLogger.warn("玩家数据不存在，玩家id=" + playerId);
            result.victory = false;
            return result;
        }

        // 验证副本结果
        if (!playerData.isGm() && !copyService.checkCopyResult(playerId, instance, result)) {
            ServerLogger.warn("验证副本失败");
            result.victory = false;
            return result;
        }
        copyService.getRewards(playerId, copyId, result);
        // 更新次数,星级
        copyService.updateCopy(playerId, instance, result);

        // 清除
        copyService.removeCopy(playerId);
        //触发神秘商店
        result.showMystery = shopService.triggerMysteryShop(playerId, copyId, 1, result);

//        //更新3星副本排行
//        ConcurrentHashMap<Integer, Copy> copys = playerData.getCopys();
//        if (copys != null) {
//            int count = 0;
//            for (Copy copy : copys.values()) {
//                if (copy.getState() >= 3) {
//                    count++;
//                }
//            }
//            copyService.updateMaxStarCopyRankings(playerId, count);
//        } else {
//            ServerLogger.warn("副本数据错误");
//        }

        return result;
    }

    // 杀死怪
    @Command(1905)
    public Object killMonster(int playerId, CMonster monster) {
        return copyService.killMonster(playerId, monster);
    }

    // 刷新副本
    public static final int CMD_GETINFO = 1901;
    public static final int CMD_REFRESH = 1906;
    public static final int CMD_REVIVI = 1907;

    // 复活
    @Command(1907)
    public Object revive(int playerId, Int2Param copy) {
        int result = copyService.revive(playerId, copy.param1, copy.param2);
        IntParam code = new IntParam();
        code.param = result;
        return code;
    }

    // 副本扫荡
    @Command(1908)
    public Object swipCopy(int playerId, Int2Param copy) {
        CopyReward result = copyService.swipeCopy(playerId, copy.param1, copy.param2);
        dailyService.refreshDailyVo(playerId);
        return result;
    }

    // 星级宝箱
    @Command(1909)
    public Object getStarRewards(int playerId, IntParam id) {
        return copyService.get3starReward(playerId, id.param);
    }

    //结束无尽漩涡
    @Command(1910)
    public Object stopEndless(int playerId, Object param) {
        IntParam code = new IntParam();
        code.param = endlessLogic.stopEndless(playerId);
        return code;
    }

    //重置无尽漩涡
    @Command(1911)
    public Object resetEndless(int playerId, Object param) {
        IntParam code = new IntParam();
        code.param = endlessLogic.resetEndless(playerId);
        return code;
    }

    //扫荡无尽漩涡
    @Command(1912)
    public Object clearEndless(int playerId, Object param) {
        IntParam code = new IntParam();
        code.param = endlessLogic.clearEndless(playerId);
        return code;
    }

    //领取无尽奖励
    @Command(1913)
    public ListParam<Reward> takeEndlessRweard(int playerId, IntParam param) {
        return endlessLogic.takeEndlessReward(playerId, param.param > 0);
    }

    //获取无尽漩涡信息
    @Command(1914)
    public EndlessInfo getEndlessInfo(int playerId, Object param) {
        return endlessLogic.getEndlessInfo(playerId);
    }

    //获取金币副本信息
    @Command(1915)
    public TreasureInfo getTreasureInfo(int playerId, Object param) {
        TreasureInfo info = new TreasureInfo();
        TreasureAttach attach = treasureLogic.getAttach(playerId);
        info.challenge = attach.getChallenge();
        info.buyTime = attach.getBuyTime();
        info.lastChallengeTime = attach.getLastChallengeTime();
        return info;
    }

    //购买金币副本挑战次数
    @Command(1916)
    public IntParam buyTreasureChallenge(int playerId, IntParam param) {
        IntParam result = new IntParam();
        result.param = treasureLogic.buyChallengeTime(playerId, param);
        return result;
    }

    //扫荡金币副本
    @Command(1917)
    public CopyReward sweepTreasure(int playerId, IntParam param) {
        return treasureLogic.sweep(playerId, param.param);
    }

    //获取经验副本信息
    @Command(1918)
    public ExperienceInfo getExperienceInfo(int playerId, Object param) {
        ExperienceInfo info = new ExperienceInfo();
        ExperienceAttach attach = experienceLogic.getAttach(playerId);
        info.challenge = attach.getChallenge();
        info.buyTime = attach.getBuyTime();
        info.lastChallengeTime = attach.getLastChallengeTime();
        return info;
    }

    //购买经验副本挑战次数
    @Command(1919)
    public IntParam buyExperienceChallenge(int playerId, IntParam param) {
        IntParam result = new IntParam();
        result.param = experienceLogic.buyChallengeTime(playerId, param);
        return result;
    }

    //扫荡经验副本
    @Command(1920)
    public CopyReward sweepExperience(int playerId, IntParam param) {
        return experienceLogic.sweep(playerId, param.param);
    }

    //副本失败,一般用于组队副本
    public static final int COPY_FAIL = 1999;//

    //扫荡娃娃机副本
    @Command(1921)
    public CopyReward sweepLeadaway(int playerId, Int2Param param) {
        return leadAwayLogic.sweep(playerId, param.param1, param.param2);
    }

    //购买娃娃机副本挑战次数
    @Command(1922)
    public IntParam buyLeadawayChallenge(int playerId, IntParam param) {
        IntParam result = new IntParam();
        result.param = leadAwayLogic.buyChallengeTime(playerId, param);
        return result;
    }

    //获取哇哇机副本信息
    @Command(1923)
    public LeadawayVO getLeadawayInfo(int playerId, Object param) {
        LeadawayVO info = new LeadawayVO();
        LeadAwayAttach attach = leadAwayLogic.getAttach(playerId);
        info.challenge = attach.getChallenge();
        info.buyTime = attach.getBuyTime();
        info.lastChallengeTime = attach.getLastChallengeTime();
        return info;
    }

    //扫荡金币机副本
    @Command(1924)
    public CopyReward sweepCatchGold(int playerId, Int2Param param) {
        return catchGoldLogic.sweep(playerId, param.param1, param.param2);
    }

    //购买金币副本挑战次数
    @Command(1925)
    public IntParam buyCatchGoldChallenge(int playerId, IntParam param) {
        IntParam result = new IntParam();
        result.param = catchGoldLogic.buyChallengeTime(playerId, param);
        return result;
    }

    //获取金币副本信息
    @Command(1926)
    public CatchGoldVO getCatchGoldInfo(int playerId, Object param) {
        CatchGoldVO info = new CatchGoldVO();
        CatchGoldAttach attach = catchGoldLogic.getAttach(playerId);
        info.challenge = attach.getChallenge();
        info.buyTime = attach.getBuyTime();
        info.lastChallengeTime = attach.getLastChallengeTime();
        return info;
    }

    //获取金币副本信息
    @Command(1928)
    public Object buyCopyTimes(int playerId, IntParam param) {
        copyService.buyMainCopyTimes(playerId, param.param);
        IntParam p = new IntParam();
        return p;
    }
}
