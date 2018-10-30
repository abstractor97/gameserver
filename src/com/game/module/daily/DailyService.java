package com.game.module.daily;

import com.game.event.InitHandler;
import com.game.module.activity.ActivityService;
import com.game.module.activity.WelfareCardService;
import com.game.module.attach.arena.ArenaLogic;
import com.game.module.attach.catchgold.CatchGoldLogic;
import com.game.module.attach.endless.EndlessLogic;
import com.game.module.attach.experience.ExperienceLogic;
import com.game.module.attach.leadaway.LeadAwayLogic;
import com.game.module.attach.lottery.LotteryLogic;
import com.game.module.attach.training.trainingLogic;
import com.game.module.attach.treasure.TreasureLogic;
import com.game.module.gang.GangDungeonService;
import com.game.module.gang.GangService;
import com.game.module.ladder.LadderService;
import com.game.module.player.PlayerData;
import com.game.module.player.PlayerService;
import com.game.module.sct.SkillCardTrainService;
import com.game.module.shop.ShopService;
import com.game.module.sign.SignService;
import com.game.module.task.TaskService;
import com.game.module.title.TitleService;
import com.game.module.vip.VipExtension;
import com.game.params.DailyVo;
import com.game.params.Int2Param;
import com.game.util.ConfigData;
import com.server.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DailyService implements InitHandler {

    public static final int VIP_DAILY_REWARD = 1;//vip每日奖励
    public static final int VIP_MONTH_CARD = 2;//vip月卡
    public static final int APPLY_GANG = 3;//当天申请帮派

    @Autowired
    private PlayerService playerService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private ShopService shopService;
    @Autowired
    private EndlessLogic endlessLogic;
    @Autowired
    private TreasureLogic treasureLogic;
    @Autowired
    private ExperienceLogic experienceLogic;
    @Autowired
    private ArenaLogic arenaLogic;
    @Autowired
    private trainingLogic trainingLogic;
    @Autowired
    private GangService gangService;
    @Autowired
    private LotteryLogic lotteryLogic;
    @Autowired
    private SignService signService;
    @Autowired
    private LeadAwayLogic leadAwayLogic;
    @Autowired
    private CatchGoldLogic catchGoldLogic;
    @Autowired
    private ActivityService activityService;
    @Autowired
    private WelfareCardService welfareCardService;
    @Autowired
    private SkillCardTrainService skillCardTrainService;
    @Autowired
    private TitleService titleService;
    @Autowired
    private LadderService ladderService;
    @Autowired
    private GangDungeonService gangDungeonService;
    public static long FIVE_CLOCK = 0;
    public static long MONDAY_FIVE_CLOCK = 0;

    @Override
    public void handleInit() {
        resetFiveClock();
    }

    public void resetFiveClock() {
        Calendar five = Calendar.getInstance();

        five.set(Calendar.HOUR_OF_DAY, 5);
        five.set(Calendar.MINUTE, 0);
        five.set(Calendar.SECOND, 0);
        five.set(Calendar.MILLISECOND, 0);

        if (five.getTimeInMillis() > System.currentTimeMillis()) {
            five.add(Calendar.DATE, -1);
        }
        FIVE_CLOCK = five.getTimeInMillis();

        five.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        if (five.getTimeInMillis() > System.currentTimeMillis()) {
            five.add(Calendar.DATE, -7);
        }
        MONDAY_FIVE_CLOCK = five.getTimeInMillis();
    }

    // 获得今天已经做了多少次
    public int getCount(int playerId, int type) {
        PlayerData playerData = playerService.getPlayerData(playerId);
        Integer count = playerData.getDailyData().get(type);
        return count == null ? 0 : count;
    }

    // 更改
    public int alterCount(int playerId, int type, int alter) {
        PlayerData playerData = playerService.getPlayerData(playerId);
        Integer count = playerData.getDailyData().get(type);
        if (count == null) {
            count = 0;
        }
        count += alter;
        if (count < 0) {
            count = 0;
        }
        playerData.getDailyData().put(type, count);
        return count;
    }

    // 设置次数
    public void setCount(int playerId, int type, int setCount) {
        PlayerData playerData = playerService.getPlayerData(playerId);
        playerData.getDailyData().put(type, setCount);
    }

    // 重置
    public void reset() {
        for (int id : SessionManager.getInstance().getAllSessions().keySet()) {
            PlayerData data = playerService.getPlayerData(id);
            resetWeeklyData(data);
            resetDailyData(data);
            // 通知前端更新副本
            int playerId = data.getPlayerId();
            // 更新每日数据
            SessionManager.getInstance().sendMsg(VipExtension.GET_DAILY_INFO, getDailyInfo(playerId), playerId);
        }
        gangService.dailyReset();
        taskService.dailyAchievement();
    }

    // 重置
    public void resetWeekly() {
        for (int id : SessionManager.getInstance().getAllSessions().keySet()) {
            titleService.updateWeekly(id, true);
            PlayerData data = playerService.getPlayerData(id);
            resetDailyData(data);
            // 通知前端更新副本
            int playerId = data.getPlayerId();
            // 更新每日数据
            SessionManager.getInstance().sendMsg(VipExtension.GET_DAILY_INFO, getDailyInfo(playerId), playerId);
        }
        ladderService.weeklyAward();
        gangDungeonService.weekly();
    }


    // 检查月卡有无过期
    public void checkMonthCardOutdate(PlayerData data) {
        if (data.getMonthCard() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= data.getMonthCardEnd()) {
            data.setMonthCard(0);
            data.setMonthCardEnd(0);
        }
    }

    public boolean isSameDate(long dailyResetTime) {
        return dailyResetTime >= FIVE_CLOCK;
    }

    public boolean isSameWeek(long weeklyResetTime) {
        return weeklyResetTime >= MONDAY_FIVE_CLOCK;
    }


    // 重置每日数据
    public void resetDailyData(PlayerData data) {
        int playerId = data.getPlayerId();
        checkMonthCardOutdate(data);
        data.getDailyData().clear();
        data.getCopyTimes().clear();
        data.setDailyTime(FIVE_CLOCK);
        shopService.dailyReset(playerId);
        signService.dailyReset(playerId);
        data.setGroupTimes(ConfigData.globalParam().groupTimes);
        // 更新每日任务
        taskService.dailyReset(playerId);
        data.setLoginDays(data.getLoginDays() + 1);
        endlessLogic.dailyReset(playerId);
        treasureLogic.dailyReset(playerId);
        leadAwayLogic.dailyReset(playerId);
        experienceLogic.dailyReset(playerId);
        arenaLogic.dailyReset(playerId);
        trainingLogic.dailyReset(playerId);
        lotteryLogic.dailyReset(playerId);
        catchGoldLogic.dailyReset(playerId);
        activityService.dailyRest(playerId);
        welfareCardService.daily(playerId);
        skillCardTrainService.dailyRest(playerId);

        //重置排位赛每日奖励次数
        data.setLadderRecordsTime(0);

        //重置活动掉落次数
        data.setActivityDropTimeMap(new ConcurrentHashMap<>());
        //重置副本次数
        data.setChallengeTimes(ConfigData.globalParam().guildCopyTimes);
    }

    public void resetWeeklyData(PlayerData data) {
        //int playerId = data.getPlayerId();
        //taskService.updateWeeklyTasks(playerId);

        //更新重置时间，放最后一行
        data.setWeeklyTime(MONDAY_FIVE_CLOCK);
    }

    // 获取每日的数据
    public DailyVo getDailyInfo(int playerId) {
        PlayerData data = playerService.getPlayerData(playerId);
        DailyVo vo = new DailyVo();
        vo.dailys = new ArrayList<>(data.getDailyData().size());
        for (Entry<Integer, Integer> e : data.getDailyData().entrySet()) {
            Int2Param d = new Int2Param();
            d.param1 = e.getKey();
            d.param2 = e.getValue();
            vo.dailys.add(d);
        }
        vo.loginDays = (short) data.getLoginDays();
        vo.monthCard = data.getMonthCard() > 0;
        vo.monthCardEnd = data.getMonthCardEnd();
        vo.charges = data.getCharges();
        vo.fundOpen = data.getFundActive();
        vo.fundsTake = data.getFunds();
        vo.vipBag = new ArrayList<>(data.getVipReward().keySet());
        vo.signFlag = data.getSignFlag();
        return vo;
    }

    // 更新每日数据到前端
    public void refreshDailyVo(int playerId) {

        SessionManager.getInstance().sendMsg(VipExtension.GET_DAILY_INFO, getDailyInfo(playerId), playerId);
    }
}
