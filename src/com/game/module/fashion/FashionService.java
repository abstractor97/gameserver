package com.game.module.fashion;

import com.game.data.*;
import com.game.module.goods.Goods;
import com.game.module.goods.GoodsEntry;
import com.game.module.goods.GoodsService;
import com.game.module.log.LogConsume;
import com.game.module.mail.MailService;
import com.game.module.player.Player;
import com.game.module.player.PlayerCalculator;
import com.game.module.player.PlayerData;
import com.game.module.player.PlayerService;
import com.game.module.serial.SerialData;
import com.game.module.serial.SerialDataService;
import com.game.module.task.Task;
import com.game.module.task.TaskService;
import com.game.params.*;
import com.game.params.rank.FashionCopyRankVO;
import com.game.params.rank.StateRankVO;
import com.game.util.ConfigData;
import com.game.util.TimeUtil;
import com.server.SessionManager;
import com.server.util.ServerLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时装管理类
 */
@Service
public class FashionService {

    // 时装部位类型
    private static final int TYPE_HEAD = 1;
    private static final int TYPE_CLOTH = 2;
    private static final int TYPE_WEAPON = 3;
    private static final int minLev = 0;//时装初始阶级

    @Autowired
    private PlayerService playerService;
    @Autowired
    private MailService mailService;
    @Autowired
    private GoodsService goodsServices;
    @Autowired
    private PlayerCalculator calculator;
    @Autowired
    private TaskService taskService;
    @Autowired
    private PlayerCalculator playerCalculator;
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private SerialDataService serialDataService;

    // 获得时装接口
    public void addFashion(int playerId, int fashionId, int limitTime) {
        // 检查职业
        FashionCfg cfg = ConfigData.getConfig(FashionCfg.class, fashionId);
        Player player = playerService.getPlayer(playerId);
        if (player.getVocation() != cfg.vocation) {
            return;
        }
        // 是否已经有了，补偿
        PlayerData data = playerService.getPlayerData(playerId);
        if (data.getFashionMap().containsKey(fashionId)) {
            // 发邮件
            List<GoodsEntry> rewards = new ArrayList<GoodsEntry>();
            rewards.add(new GoodsEntry(Goods.DIAMOND, cfg.duplicateReturn));
            String title = mailService.getCode(Response.DUPLICATE_FASHION_TITLE);
            String content = mailService.getCode(Response.DUPLICATE_FASHION_CONTENT);
            mailService.sendSysMail(title, content, rewards, playerId, LogConsume.GM);
            return;
        }

        data.getFashions().add(fashionId);
        data.getFashionMap().put(fashionId, new Fashion(fashionId, System.currentTimeMillis(), cfg.timeLimit));

        taskService.doTask(playerId, Task.TYPE_FASH_COUNT, data.getFashionMap().size());
        calculator.calculate(player);
        // 推送前端
        SessionManager.getInstance().sendMsg(FashionExtension.GET_INFO, getFashionInfo(playerId), playerId);
    }

    // 获取时装信息
    public FashionInfo getFashionInfo(int playerId) {
        checkRemoveTimeoutFashions(playerId, false);
        FashionInfo info = new FashionInfo();
        info.fashions = new ArrayList<>();

        PlayerData data = playerService.getPlayerData(playerId);

        //获取时装阶级数据
        Set<Integer> fashionRankSet = data.getFashionRankSet();
        if (fashionRankSet == null) {
            fashionRankSet = new HashSet<>();
        }
        Collection<Object> configs = ConfigData.getConfigs(FashionUpCfg.class);

        for (Fashion fashion : data.getFashionMap().values()) {
            FashionVO vo = new FashionVO();
            vo.createTime = fashion.getCreateTime();
            vo.id = fashion.getId();
            vo.period = fashion.getPeriod();

            //是否已有
            boolean isExisting = false;
            for (Integer id : fashionRankSet) {
                FashionUpCfg config = ConfigData.getConfig(FashionUpCfg.class, id);
                if (config != null && config.FashionID == fashion.getId()) {
                    isExisting = true;
                    vo.stage = config.lev;
                    break;
                }
            }

            //初始化阶级
            if (!isExisting) {
                for (Object object : configs) {
                    FashionUpCfg fashionUpCfg = (FashionUpCfg) object;
                    if (fashionUpCfg.FashionID == fashion.getId() && fashionUpCfg.lev == minLev) {
                        fashionRankSet.add(fashionUpCfg.id);
                        break;
                    }
                }
            }

            info.fashions.add(vo);
        }

        Player player = playerService.getPlayer(playerId);
        info.cloth = player.getFashionId();
        info.weapon = player.getWeaponId();
        info.head = data.getCurHead();

        return info;
    }

    // 激活时装
    public int active(int playerId, int fashionId, boolean isBuy) {
        FashionCfg cfg = ConfigData.getConfig(FashionCfg.class, fashionId);
        if (cfg == null) {
            ServerLogger.warn("时装不存在，时装ID=" + fashionId);
            return Response.ERR_PARAM;
        }
        // 判断职业
        Player player = playerService.getPlayer(playerId);
        PlayerData data = playerService.getPlayerData(playerId);
        if (player.getVocation() != cfg.vocation) {
            return Response.NO_VOCATION;
        }
        // 已经拥有了
        if (data.getFashionMap().containsKey(fashionId)) {
            return Response.ERR_PARAM;
        }
        // 判断条件
        if (cfg.limitType == 1 && player.getVip() < cfg.limitParams[0]) {
            return Response.NO_VIP;
        }

        // 扣除消耗
        if (isBuy) {
            List<GoodsEntry> costs = Arrays.asList(new GoodsEntry(cfg.price[0], cfg.price[1]));
            int costResult = goodsServices.decConsume(playerId, costs, LogConsume.ACTIVE_FASHION, fashionId);
            if (costResult != Response.SUCCESS) {
                return costResult;
            }
        }

        data.getFashionMap().put(fashionId, new Fashion(fashionId, System.currentTimeMillis(), cfg.timeLimit));
        // 推送前端
        SessionManager.getInstance().sendMsg(FashionExtension.GET_INFO, getFashionInfo(playerId), playerId);
        calculator.calculate(player);

        //更新时装排行
//        updateFashionRankings(playerId, data.getGlamour());

        return Response.SUCCESS;
    }

    /**
     * @param playerId
     * @param fashionId
     * @return
     */
    public int updateToForever(int playerId, int fashionId) {
        FashionCfg cfg = ConfigData.getConfig(FashionCfg.class, fashionId);
        if (cfg == null) {
            ServerLogger.warn("fashion dont exist,fashionId = {}", fashionId);
            return Response.ERR_PARAM;
        }

        PlayerData data = playerService.getPlayerData(playerId);
        Fashion fashion = data.getFashionMap().get(fashionId);
        if (fashion == null) {
            ServerLogger.warn("fashion dont exist,fashionId = {}", fashionId);
            return Response.ERR_PARAM;
        }

        // 扣除消耗
        List<GoodsEntry> costs = Arrays.asList(new GoodsEntry(cfg.costPrice[0], cfg.costPrice[1]));
        int costResult = goodsServices.decConsume(playerId, costs, LogConsume.ACTIVE_FASHION, fashionId);
        if (costResult != Response.SUCCESS) {
            return costResult;
        }

        fashion.setPeriod(0);
        SessionManager.getInstance().sendMsg(FashionExtension.GET_INFO, getFashionInfo(playerId), playerId);
        return Response.SUCCESS;
    }

    // 替换时装
    public TakeFashionVO replace(int playerId, int type, int fashionId) {
        PlayerData data = playerService.getPlayerData(playerId);
        Player player = playerService.getPlayer(playerId);
        TakeFashionVO resp = new TakeFashionVO();
        resp.type = type;
        resp.fashionId = fashionId;
        resp.errCode = Response.SUCCESS;

        // 是否拥有
        if (fashionId > 0) {
            Fashion fashion = data.getFashionMap().get(fashionId);
            if (fashion == null) {
                resp.errCode = Response.ERR_PARAM;
                return resp;
            }
            if (fashion.getPeriod() != 0 && System.currentTimeMillis() >= fashion.getCreateTime() + fashion.getPeriod() * 1000) {
                resp.errCode = Response.FASHION_TIME_OUT;
                return resp;
            }
            // 检查参数
            FashionCfg cfg = ConfigData.getConfig(FashionCfg.class, fashionId);
            if (cfg.type != type) {
                resp.errCode = Response.ERR_PARAM;
                return resp;
            }
        }
        // 更新数据
        if (fashionId == 0) {
            if (type == TYPE_CLOTH) {
                player.setFashionId(ConfigData.globalParam().fashionId[player.getVocation() - 1]);
            } else if (type == TYPE_WEAPON) {
                player.setWeaponId(ConfigData.globalParam().weaponId[player.getVocation() - 1]);
            } else if (type == TYPE_HEAD) {
                data.setCurHead(ConfigData.globalParam().headId[player.getVocation() - 1]);
            }
        } else {
            if (type == TYPE_CLOTH) {
                player.setFashionId(fashionId);
            } else if (type == TYPE_WEAPON) {
                player.setWeaponId(fashionId);
            } else if (type == TYPE_HEAD) {
                data.setCurHead(fashionId);
            }
        }
        calculator.calculate(player);
        return resp;
    }

    /**
     * 检测并移除过期时装
     *
     * @param playerId
     * @param needNotify
     */
    public void checkRemoveTimeoutFashions(int playerId, boolean needNotify) {
        PlayerData data = playerService.getPlayerData(playerId);
        // 检测
        Player player = playerService.getPlayer(playerId);
        List<Integer> dels = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (data == null) {
            return;
        }
        for (Fashion fashion : data.getFashionMap().values()) {
            if (fashion.getPeriod() != 0 && now >= fashion.getCreateTime() + fashion.getPeriod() * 1000) {
                dels.add(fashion.getId());
            }
        }
        for (Integer id : dels) {
            data.getTempFashions().remove(id);
            data.getFashions().remove(id);
            data.getFashionMap().remove(id);

            FashionCfg cfg = ConfigData.getConfig(FashionCfg.class, id);
            if (cfg.type == TYPE_CLOTH) {
                player.setFashionId(ConfigData.globalParam().fashionId[player.getVocation() - 1]);
            } else if (cfg.type == TYPE_WEAPON) {
                player.setWeaponId(ConfigData.globalParam().weaponId[player.getVocation() - 1]);
            } else if (cfg.type == TYPE_HEAD) {
                data.setCurHead(ConfigData.globalParam().headId[player.getVocation() - 1]);
            }
        }
        if (!dels.isEmpty()) {
            calculator.calculate(player);
        }
        if (!dels.isEmpty() && needNotify) {
            // 推送前端
            SessionManager.getInstance().sendMsg(FashionExtension.GET_INFO, getFashionInfo(playerId), playerId);
        }
    }

    public void getAllFashion(int playerId) {

        for (Object o : ConfigData.getConfigs(FashionCfg.class)) {
            FashionCfg cfg = (FashionCfg) o;
            addFashion(playerId, cfg.id, cfg.timeLimit);
        }
    }

    /**
     * 时装升阶
     *
     * @param playerId  玩家id
     * @param fashionId 时装id
     * @return 错误码
     */
    public IntParam upgradeRank(int playerId, int fashionId) {
        IntParam param = new IntParam();
        PlayerData playerData = playerService.getPlayerData(playerId);
        if (playerData == null) {
            param.param = Response.ERR_PARAM;
            ServerLogger.warn("玩家数据不存在，玩家id=" + playerId);
            return param;
        }

        Set<Integer> fashionRankSet = playerData.getFashionRankSet();
        if (fashionRankSet == null || fashionRankSet.isEmpty()) {
            param.param = Response.ERR_PARAM;
            ServerLogger.warn("时装信息错误，玩家id=" + playerId);
            return param;
        }

        for (Integer id : fashionRankSet) {
            FashionUpCfg config = ConfigData.getConfig(FashionUpCfg.class, id);

            if (fashionId != config.FashionID) {
                continue;
            }

            if (config.nextID == 0) {
                param.param = Response.MAX_LEV;
                return param;
            }

            //扣除材料
            if (goodsService.decConsume(playerId, config.cost, LogConsume.FASHION_UPGRADE, config.id) != Response.SUCCESS) {
                param.param = Response.NO_MATERIAL;
                return param;
            }

            //时装升阶
            fashionRankSet.add(config.nextID);
            fashionRankSet.remove(config.id);
            //推送前端
            SessionManager.getInstance().sendMsg(FashionExtension.GET_INFO, getFashionInfo(playerId), playerId);
            //更新属性
            playerCalculator.calculate(playerId);
            param.param = Response.SUCCESS;
            break;
        }

        //更新时装排行
//        updateFashionRankings(playerId, playerData.getGlamour());

        return param;
    }

    //时装排行榜
//    public ListParam<FashionCopyRankVO> getFashionRankings() {
//        ListParam listParam = new ListParam();
//
//        SerialData serialData = serialDataService.getData();
//        if (serialData == null) {
//            ServerLogger.warn("序列化数据不存在");
//            listParam.code = Response.ERR_PARAM;
//            return listParam;
//        }
//
//        listParam.params = new ArrayList<>(serialDataService.getData().getFashionRankingsMap().values());
//        Collections.sort(listParam.params, COMPARATOR);
//        return listParam;
//    }
//
//    //时装排序
//    private static final Comparator<FashionCopyRankVO> COMPARATOR = new Comparator<FashionCopyRankVO>() {
//        @Override
//        public int compare(FashionCopyRankVO o1, FashionCopyRankVO o2) {
//            if (o1.glamour == o2.glamour) {
//                return (int) (o2.fightingValue - o1.fightingValue);
//            }
//            return o2.glamour - o1.glamour;
//        }
//    };
//
//    //更新时装排行
//    private void updateFashionRankings(int playerId, int glamour) {
//        //时装排行
//        Player player = playerService.getPlayer(playerId);
//        if (player == null) {
//            ServerLogger.warn("玩家不存在，玩家ID=" + playerId);
//            return;
//        }
//
//        SerialData serialData = serialDataService.getData();
//        if (serialData == null) {
//            ServerLogger.warn("序列化数据不存在");
//            return;
//        }
//        FashionCopyRankVO fashionCopyRankVO = new FashionCopyRankVO();
//        fashionCopyRankVO.name = player.getName();
//        fashionCopyRankVO.level = player.getLev();
//        fashionCopyRankVO.vocation = player.getVocation();
//        fashionCopyRankVO.fightingValue = player.getFight();
//        fashionCopyRankVO.playerId = playerId;
//        fashionCopyRankVO.glamour = glamour;
//        serialDataService.getData().getFashionRankingsMap().put(playerId, fashionCopyRankVO);
//    }
}
