package com.game.module.gang;

import com.game.SysConfig;
import com.game.data.*;
import com.game.event.InitHandler;
import com.game.module.activity.ActivityConsts;
import com.game.module.activity.ActivityService;
import com.game.module.admin.ManagerService;
import com.game.module.chat.ChatExtension;
import com.game.module.chat.ChatService;
import com.game.module.daily.DailyService;
import com.game.module.goods.Goods;
import com.game.module.goods.GoodsEntry;
import com.game.module.goods.GoodsService;
import com.game.module.log.LogConsume;
import com.game.module.mail.MailService;
import com.game.module.player.Player;
import com.game.module.player.PlayerCalculator;
import com.game.module.player.PlayerData;
import com.game.module.player.PlayerService;
import com.game.module.scene.SceneService;
import com.game.module.serial.PlayerView;
import com.game.module.serial.SerialDataService;
import com.game.module.task.Task;
import com.game.module.task.TaskService;
import com.game.params.*;
import com.game.params.chat.ChatVo;
import com.game.params.gang.*;
import com.game.util.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.server.SessionManager;
import com.server.util.GameData;
import com.server.util.ServerLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class GangService implements InitHandler {

    @Autowired
    private GangDao gangDao;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private DailyService dailyService;
    @Autowired
    private MailService mailService;
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private PlayerCalculator calculator;
    @Autowired
    private TaskService taskService;
    @Autowired
    private ManagerService managerService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private SerialDataService serialService;
    @Autowired
    private SerialDataService serialDataService;
    @Autowired
    private TimerService timerService;
    @Autowired
    private SceneService sceneService;
    @Autowired
    private ActivityService activityService;

    private Map<Integer, Gang> gangs = new ConcurrentHashMap<>();
    private Map<String, Integer> gangNames = new ConcurrentHashMap<>();
    private List<Gang> orderGangs = null;

    private static volatile int maxGangId = 0;

    @Override
    public void handleInit() {
        Integer curMaxGangId = gangDao.selectMaxGangId();
        if (curMaxGangId == null) {
            curMaxGangId = 0;
        }
        maxGangId = (curMaxGangId / 1000);
        for (byte[] gdb : gangDao.selectGangs()) {
            if (gdb != null) {
                byte[] g = CompressUtil.decompressBytes(gdb);
                Gang gang = JsonUtils.string2Object(
                        new String(g, Charset.forName("utf-8")), Gang.class);
                if (gang == null) { //屏蔽错误
                    gang = JsonUtils.string2Object(
                            new String(gdb, Charset.forName("utf-8")), Gang.class);
                    if (gang == null) {
                        continue;
                    }
                }
                gangs.put(gang.getId(), gang);
                gangNames.put(gang.getName(), gang.getId());
                if (gang.getCreateDate() == null) {
                    gang.setCreateDate(Calendar.getInstance());
                }
                GTRoom room = gang.getGtRoom();
                if (room != null) {
                    GangTrainingCfg cfg = GameData.getConfig(GangTrainingCfg.class, room.getId());
                    int minute = cfg.maxTime - (int) ((System.currentTimeMillis() - room.getCreateTime()) / TimeUtil.ONE_MIN);
                    CloseTask task = new CloseTask(gang);
                    if (minute <= 0) {
                        task.run();
                    } else {
                        Future<?> future = Context.getTimerService().scheduleDelay(task, minute, TimeUnit.MINUTES);
                        task.future = future;
                        room.setCloseTask(task);
                    }
                }
            }
        }

        //每N分钟检测保存一次
        timerService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    update();
                } catch (Exception e) {
                    ServerLogger.err(e, "公会保存数据异常");
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
        sort();
    }

    public void dailyReset() {
        long lastResetTime = serialService.getData().getGangDailyReset();
        if (DailyService.FIVE_CLOCK <= lastResetTime) {
            return;
        }

        serialService.getData().setGangDailyReset(DailyService.FIVE_CLOCK);

        for (Gang gang : gangs.values()) {
            resetGangTask(gang);
            gang.setUpdated(true);
        }

    }

    private void resetGangTask(Gang gang) {
        List<Integer> allGangTasks = ConfigData.getGangTasks();
        int size = allGangTasks.size();
        Map<Integer, Task> gTasks = new HashMap<Integer, Task>();
        Set<Integer> types = new HashSet<Integer>();
        for (int id : ConfigData.globalParam().gangTasks) {
            TaskConfig cfg = GameData.getConfig(TaskConfig.class, id);
            types.add(cfg.finishType);
            gTasks.put(id, new Task(id, Task.STATE_ACCEPTED, cfg.finishType));
        }
        int tmp = 0;
        while (gTasks.size() < 8 && tmp++ < 50) {//防止策划数据太少进入死循环
            int index = RandomUtil.randInt(size);
            int id = allGangTasks.get(index);
            if (gTasks.containsKey(id)) {
                continue;
            }
            TaskConfig cfg = GameData.getConfig(TaskConfig.class, id);
            if (types.contains(cfg.finishType)) {
                continue;
            }
            types.add(cfg.finishType);
            gTasks.put(id, new Task(id, Task.STATE_ACCEPTED, cfg.taskType));
        }
        gang.setTasks(gTasks);
    }

    public Gang getGang(int id) {
        return gangs.get(id);
    }

    public GangInfo getSpecialGang(int playerId, int id) {
        Gang gang = gangs.get(id);
        if (gang != null) {
            GangInfo info = toGangVo(gang);
            info.apply = gang.getApplys().containsKey(playerId);
            return info;
        }
        return null;
    }

    // 获取一个新的帮派id
    private synchronized int getNextGangId() {
        maxGangId++;
        return maxGangId * 1000 + SysConfig.serverId;
    }

    public boolean isAdmin(int playerId, Gang gang) {
        return playerId == gang.getOwnerId()
                || gang.getAdmins().contains(playerId);
    }

    // 创建帮派
    public int create(int playerId, String name, String notice) {
        // 已经有帮派
        Player player = playerService.getPlayer(playerId);
        if (player.getGangId() > 0) {
            return Response.ERR_PARAM;
        }
        if (name.length() < 2 || name.length() > 6) {
            return Response.ERR_PARAM;
        }
        if (notice.length() > 50) {
            return Response.ERR_PARAM;
        }
        // 同名
        if (gangNames.get(name) != null) {
            return Response.GANG_SAME_NAME;
        }
        PlayerData data = playerService.getPlayerData(playerId);
        if (data.getLastQuitGang() > 0) { //24小时判断
            if ((System.currentTimeMillis() - data.getLastQuitGang()) <= TimeUtil.ONE_HOUR
                    * ConfigData.globalParam().quitPunish) {
                return Response.QUIT_GANG_LAST;
            }
        }
        GlobalConfig global = ConfigData.globalParam();
        // 验证等级
        if (player.getLev() < global.createGangLev) {
            return Response.NO_LEV;
        }
        // 验证钻石
        if (!playerService.decDiamond(playerId, global.createGangDiamond,
                LogConsume.CREATE_GANG)) {
            return Response.NO_DIAMOND;
        }
        BeanManager.getBean(PlayerCalculator.class).calculate(playerId);
        // 生成数据
        int gangId = getNextGangId();
        Gang gang = new Gang();
        gang.setId(gangId);
        gang.setOwnerId(playerId);
        gang.setName(name);
        gang.setNotice(notice);
        gang.setLev(1);
        gang.setRank(orderGangs.size() + 1);
        gang.getMembers().put(playerId, new GMember(playerId, Gang.ADMIN, ConfigData.globalParam().guildCopyTimes));
        GangBuildCfg build = ConfigData.getConfig(GangBuildCfg.class,
                Gang.MAIN_BUILD * 100 + 1);
        gang.setCreateDate(Calendar.getInstance());
        gang.setTotalFight(player.getFight());
        gang.setMaxNum(build.memberCount);
        gangs.put(gangId, gang);
        gangNames.put(gang.getName(), gang.getId());
        orderGangs.add(gang);
        player.setGangId(gangId);
        gang.setUpdated(true);
        sort(false);
        resetGangTask(gang);

        //初始解锁
        for (Object obj : GameData.getConfigs(GangScienceCfg.class)) {
            GangScienceCfg cfg = (GangScienceCfg) obj;
            if (cfg.lv == 0 && cfg.NeedLevel == gang.getLev()) {
                gang.getTechnologys().add(cfg.id);
            }
        }
        String str = JsonUtils.object2String(gang);
        byte[] dbData = str.getBytes(Charset.forName("utf-8"));
        gangDao.insert(gang.getId(), CompressUtil.compressBytes(dbData));

        //公会等级活动
        activityService.tour(playerId, ActivityConsts.ActivityTaskCondType.T_GUILD_GRADE, gang.getLev());

        return Response.SUCCESS;
    }

    // 获取帮派列表
    private int pageSize = 10;

    public GangList getGangList(int playerId, int page) {
        GangList list = new GangList();
        list.page = gangs.size() / pageSize;
        if (gangs.size() % pageSize != 0) {
            list.page++;
        }
        if (page == 0) page = 1;
        list.curPage = page;
        list.gangs = new ArrayList<>(pageSize);

        int begin = (page - 1) * pageSize;
        int end = Math.min(begin + pageSize, orderGangs.size());
        for (int i = begin; i < end; i++) {
            Gang gang = orderGangs.get(i);
            if (gang == null) {
                continue;
            }
            GangInfo vo = toGangVo(gang);
            if (vo == null) {
                gangs.remove(gang.getId());
                continue;
            }
            vo.apply = gang.getApplys().containsKey(playerId);
            list.gangs.add(vo);
        }
        return list;
    }

    // 转前端vo
    private GangInfo toGangVo(Gang gang) {
        GangInfo vo = new GangInfo();
        vo.id = gang.getId();
        vo.count = gang.getMembers().size();
        vo.maxCount = gang.getMaxNum();
        vo.lev = gang.getLev();
        vo.name = gang.getName();
        vo.notice = gang.getNotice();
        Player player = playerService.getPlayer(gang.getOwnerId());

        if (player == null) {
            return null;
        }
        vo.owner = player.getName();
        vo.ownerLev = player.getLev();
        vo.ownerVocation = player.getVocation();
        vo.totalFight = gang.getTotalFight();
        vo.fightLimit = gang.getFightLimit();
        vo.levLimit = gang.getLevLimit();
        vo.isLevLimit = gang.isLimitLev();
        vo.isFightLimit = gang.isLimitFight();
        vo.rank = gang.getRank();
        vo.ownerId = player.getPlayerId();
        vo.ownerFightValue = player.getFight();
        return vo;
    }

    // 我的帮派信息
    public MyGangInfo getMyGang(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Gang myGang = gangs.get(player.getGangId());
        MyGangInfo vo = new MyGangInfo();
        if (myGang == null) {
            return vo;
        }
        vo.basicInfo = toGangVo(myGang);
        vo.autoJoin = myGang.getAutoJoin();
        vo.asset = myGang.getAsset();
        vo.totalAsset = myGang.getTotalAsset();
        vo.rank = myGang.getRank();

        GMember my = myGang.getMembers().get(playerId);
        if (my == null) {
            return vo;
        }
        vo.myPosition = my.getPosition();

        // 帮派建筑
        vo.builds = new ArrayList<GangBuild>(myGang.getBuildings().size());
        for (Entry<Integer, Integer> b : myGang.getBuildings().entrySet()) {
            GangBuild build = new GangBuild();
            build.type = b.getKey();
            build.lev = b.getValue();
            vo.builds.add(build);
        }

        vo.technology = new ArrayList<>();
        vo.technology.addAll(myGang.getTechnologys());

        //公会等级活动
        activityService.tour(playerId, ActivityConsts.ActivityTaskCondType.T_GUILD_GRADE, myGang.getLev());

        return vo;
    }

    // 获取帮派成员
    public List<GangMember> getMembers(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Gang myGang = gangs.get(player.getGangId());
        if (myGang == null) {
            return null;
        }
        // 成员信息
        List<GangMember> members = new ArrayList<GangMember>(myGang
                .getMembers().size());
        for (GMember gm : myGang.getMembers().values()) {
            int memberId = gm.getPlayerId();
            Player info = playerService.getPlayer(memberId);
            GangMember member = new GangMember();
            member.playerId = memberId;
            member.donate7 = gm.getContribute7();
            member.fightStrength = info.getFight();
            member.lev = info.getLev();
            member.name = info.getName();
            member.vip = info.getVip();
            member.vocation = info.getVocation();
            member.online = SessionManager.getInstance().getChannel(memberId) != null;
            member.position = gm.getPosition();
            member.taskContribution = gm.getTaskContribution();
            members.add(member);
        }
        return members;
    }

    // 获取申请列表
    public List<GangApply> getApplys(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Gang myGang = gangs.get(player.getGangId());
        // 申请列表
        if (myGang == null) {
            return null;
        }
        List<GangApply> applys = new ArrayList<GangApply>(myGang.getApplys()
                .size());
        for (int applyId : myGang.getApplys().keySet()) {
            Player info = playerService.getPlayer(applyId);
            GangApply apply = new GangApply();
            apply.fightStrength = info.getFight();
            apply.lev = info.getLev();
            apply.name = info.getName();
            apply.playerId = info.getPlayerId();
            apply.vocation = info.getVocation();
            apply.vip = info.getVip();
            if (SessionManager.getInstance().isActive(info.getPlayerId())) {
                apply.lastLogin = 0L;
            } else {
                apply.lastLogin = info.getLastLoginTime().getTime();
            }
            applys.add(apply);
        }
        return applys;
    }

    // 申请入帮
    public Int2Param apply(int playerId, int gangId) {
        Int2Param int2Param = new Int2Param();
        // 自己已经有帮派
        Player player = playerService.getPlayer(playerId);
        if (player.getGangId() > 0) {
            int2Param.param1 = Response.HAS_GANG;
            return int2Param;
        }
        Gang gang = gangs.get(gangId);
        if (gang.getMaxNum() <= gang.getMembers().size()) {
            int2Param.param1 = Response.GANG_FULL;
            return int2Param;
        }
        // 重复申请
        if (gang.getApplys().containsKey(playerId)) {
            int2Param.param1 = Response.GANG_HAS_APPLY;
            return int2Param;
        }
        GlobalConfig global = ConfigData.globalParam();
        // 验证申请次数
        /*if (gang.getApplys().size() >= global.applyLimit) {
            return Response.GANG_APPLY_MAX;
		}*/

        // 退出时间
        PlayerData data = playerService.getPlayerData(playerId);
        if (!data.getModules().contains(1041)) { //公会功能未开启
            int2Param.param1 = Response.GUILD_DONT_OPEN;
            return int2Param;
        }
//        if (data.getLastQuitGang() > 0) {
//            if ((System.currentTimeMillis() - data.getLastQuitGang()) <= TimeUtil.ONE_HOUR * global.quitPunish) {
//                int2Param.param1 = Response.QUIT_GANG_LAST;
//                int2Param.param2 = (int) ((System.currentTimeMillis() - data.getLastQuitGang()) / TimeUtil.ONE_HOUR);
//                return int2Param;
//            }
//        }
        // 条件不足(等级,战斗力)
        if ((gang.isLimitFight() && gang.getFightLimit() > player.getFight())
                || (gang.isLimitLev() && gang.getLevLimit() > player.getLev())) {
            int2Param.param1 = Response.GANG_APPLY_LIMIT;// 战斗力或等级不足
            return int2Param;
        }

        // 自动加入
        if (gang.getAutoJoin()) {
            approve(gang.getOwnerId(), playerId);
        } else {
            // 加入申请列表
            gang.getApplys().put(playerId, System.currentTimeMillis());
            ListParam<GangApply> applys = new ListParam<GangApply>();
            applys.params = getApplys(gang.getOwnerId());
            for (GMember member : gang.getMembers().values()) {
                if (member.getPosition() == Gang.ADMIN) {
                    SessionManager.getInstance().sendMsg(2517, applys, member.getPlayerId());
                }
            }
            int2Param.param1 = Response.JOIN_SUCESS_TITLE;
            return int2Param;
        }
        gang.setUpdated(true);
        dailyService.alterCount(playerId, DailyService.APPLY_GANG, 1);
        int2Param.param1 = Response.SUCCESS;
        return int2Param;
    }

    // 批准
    public int approve(int playerId, int applyId) {
        // 验证是否有权限
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (!isAdmin(playerId, gang)) {
            return Response.GANG_NO_PRIVILEGE;
        }
        // 对方已经有帮派啦，申请失效
        Player applyer = playerService.getPlayer(applyId);
        if (applyer.getGangId() > 0) {
            gang.getApplys().remove(applyId);
            return Response.APPLY_OUTDATED;
        }

        PlayerData playerData = playerService.getPlayerData(applyId);
//        if (playerData.getLastQuitGang() > 0) {
//            if ((System.currentTimeMillis() - playerData.getLastQuitGang()) <= TimeUtil.ONE_HOUR
//                    * ConfigData.globalParam().quitPunish) {
//                gang.getApplys().remove(applyId);
//                gang.setUpdated(true);
//                return Response.QUIT_GANG_LAST;
//            }
//        }

        synchronized (gang) {
            // 本帮人员已满
            if (gang.getMembers().size() >= gang.getMaxNum()) {
                return Response.GANG_FULL;
            }
            gang.getMembers().put(applyId, new GMember(applyId, ConfigData.globalParam().guildCopyTimes));

        }

        // 更新对方
        synchronized (applyer) {
            if (applyer.getGangId() == 0) {
                applyer.setGangId(gangId);
                gang.getApplys().remove(applyId);
                playerService.update(applyer);
            } else {
                gang.getMembers().remove(applyId);
                return Response.APPLY_OUTDATED;
            }
        }

        gang.setUpdated(true);
        // 发送邮件
        String title = ConfigData.getConfig(ErrCode.class, Response.JOIN_SUCESS_TITLE).tips;
        String content = ConfigData.getConfig(ErrCode.class, Response.JOIN_SUCESS_CONTENT).tips;
        mailService.sendSysMail(title, String.format(content, gang.getName()), null, applyId, null);

        IntParam param = new IntParam();
        SessionManager.getInstance().sendMsg(2505, param, applyId);
        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG, getMyGang(applyId), applyId);

        calculator.calculate(applyer);

        //公会成就
        Map<Integer, int[]> condParam = Maps.newHashMap();
        int openLv = 0;
        for (int technologyId : gang.getTechnologys()) {
            GangScienceCfg conf = ConfigData.getConfig(GangScienceCfg.class, technologyId);
            if (openLv < conf.NeedLevel) {
                openLv = conf.NeedLevel;
            }
        }
        condParam.put(Task.TYPE_GANG_TEC, new int[]{openLv});
        condParam.put(Task.TYPE_GANG_LEVEL, new int[]{gang.getLev()});
        condParam.put(Task.TYPE_GANG_RANK, new int[]{gang.getRank()});
        GangDungeon gangDungeon = serialDataService.getData().getGangMap().get(player.getGangId());
        if (gangDungeon != null) {
            int layer;
            if (gangDungeon.getHasOpen() == GangDungeonService.T_PASS) {
                layer = gangDungeon.getLayer();
            } else {
                layer = gangDungeon.getLayer() - 1;
            }
            condParam.put(Task.TYPE_GUILD_COPY, new int[]{layer});
        }
        gang.setUpdated(true);
        taskService.doTask(applyId, condParam);

        //公会等级活动
        activityService.tour(applyId, ActivityConsts.ActivityTaskCondType.T_GUILD_GRADE, gang.getLev());

        //公会任务活动
        int count = 0;
        for (Task task : gang.getTasks().values()) {
            if (task.getState() == Task.STATE_SUBMITED) {
                count++;
            }
        }
        activityService.tour(applyId, ActivityConsts.ActivityTaskCondType.T_GUILD_TASK, count);

        return Response.SUCCESS;
    }

    // 拒绝
    public int refuse(int playerId, int applyId) {
        // 验证是否有权限
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (!isAdmin(playerId, gang)) {
            return Response.GANG_NO_PRIVILEGE;
        }
        gang.getApplys().remove(applyId);
        gang.setUpdated(true);
        return Response.SUCCESS;
    }

    // 踢人
    public int kick(int playerId, int kickId) {
        // 验证是否有权限
        if (playerId == kickId) {
            return Response.ERR_PARAM;
        }
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (!isAdmin(playerId, gang)) {
            return Response.GANG_NO_PRIVILEGE;
        }
        Player kicker = playerService.getPlayer(kickId);
        //sendTrainingReward(kickId);
        // 更新玩家信息
        synchronized (gang) {
            gang.getMembers().remove(kickId);
            gang.getAdmins().remove(kickId);
        }
        synchronized (kicker) {
            if (kicker.getGangId() == gangId) {
                String key = sceneService.getGroupKey(player);
                SessionManager.getInstance().removeFromGroup(key, player.getPlayerId());
                kicker.setGangId(0);
                playerService.update(player);
            }
        }
        gang.setUpdated(true);
        // 发送邮件
        String content = ConfigData
                .getConfig(ErrCode.class, Response.KICK_GANG).tips;
        content = String.format(content, gang.getName());
        mailService.sendSysMail(content, content, null, kickId, null);

        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                getMyGang(kickId), kickId);

        // 设置退出时间
        PlayerData data = playerService.getPlayerData(kickId);
        data.setLastQuitGang(System.currentTimeMillis());
        playerService.updatePlayerData(kickId);
        gang.setUpdated(true);
        calculator.calculate(kickId);
        return Response.SUCCESS;
    }

    // 退出
    public int quit(int playerId) {
        // 设置玩家信息
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (gang == null) {
            return Response.ERR_PARAM;
        }
        if (gang.getOwnerId() == playerId) {
            return Response.ERR_PARAM;
        }
        //sendTrainingReward(playerId);
        synchronized (gang) {
            gang.getMembers().remove(playerId);
            gang.getAdmins().remove(playerId);
        }

        synchronized (player) {
            if (player.getGangId() == gangId) {
                String key = sceneService.getGroupKey(player);
                SessionManager.getInstance().removeFromGroup(key, player.getPlayerId());
                player.setGangId(0);
            }
        }
        calculator.calculate(player);
        gang.setUpdated(true);
        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                getMyGang(playerId), playerId);

        // 设置退出时间
        PlayerData data = playerService.getPlayerData(playerId);
        data.setLastQuitGang(System.currentTimeMillis());
        gang.setUpdated(true);
        return Response.SUCCESS;
    }

    // 转让
    public int transfer(int playerId, int newOwnerId) {
        // 不能转让给会长本人
        if (playerId == newOwnerId) {
            return Response.GANG_NO_PRIVILEGE;
        }
        // 验证是否有权限
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (playerId != gang.getOwnerId()) {
            return Response.GANG_NO_PRIVILEGE;// 权限不够
        }
        // 更新帮派的数据
        synchronized (gang) {
            gang.getMembers().get(playerId).setPosition(Gang.MEMBER);
            ServerLogger.info("#transfer change leader,leader id = " + newOwnerId);
            gang.setOwnerId(newOwnerId);
            gang.getMembers().get(newOwnerId).setPosition(Gang.ADMIN);
        }

        Player newOwner = playerService.getPlayer(newOwnerId);
        // 发送邮件
        String mail = ConfigData
                .getConfig(ErrCode.class, Response.BE_NEW_OWNER).tips;
        mailService.sendSysMail(mail, mail, null, newOwnerId, null);

        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                getMyGang(playerId), playerId);
        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                getMyGang(newOwnerId), newOwnerId);

        // 全部帮派成员发送邮件
        String title = ConfigData.getConfig(ErrCode.class,
                Response.GANG_TRANSFER_TITLE).tips;
        String content = String.format(ConfigData.getConfig(ErrCode.class,
                Response.GANG_TRANSFER_CONTENT).tips, newOwner.getName());
        managerService.sendMail(title, content, null, new ArrayList<Integer>(
                gang.getMembers().keySet()));

        gang.getAdmins().remove(playerId);
        gang.setUpdated(true);
        return Response.SUCCESS;
    }

    // 解除副会长
    public int removeViceAdmin(int playerId, int viceId) {
        // 验证是否有权限
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (playerId != gang.getOwnerId()) {
            return Response.GANG_NO_PRIVILEGE;// 权限不够
        }
        GMember member = gang.getMembers().get(viceId);
        if (member == null || member.getPosition() != Gang.VICE_ADMIN) {
            return Response.ERR_PARAM;
        }
        member.setPosition(Gang.MEMBER);
        gang.getAdmins().remove(viceId);
        gang.setUpdated(true);

        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                getMyGang(viceId), viceId);

        return Response.SUCCESS;
    }

    // 设置副会长
    public int setViceOwner(int playerId, int viceId) {
        if (playerId == viceId) {
            return Response.SET_POS_OWNER;
        }
        // 验证是否有权限
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (playerId != gang.getOwnerId()) {
            return Response.GANG_NO_PRIVILEGE;// 权限不够
        }
        GMember member = gang.getMembers().get(viceId);
        if (member.getPosition() == Gang.VICE_ADMIN) {
            return Response.SUCCESS;
        }
        GangBuildCfg nextBuild = ConfigData.getConfig(GangBuildCfg.class,
                Gang.MAIN_BUILD * 100 + gang.getLev());
        if (gang.getAdmins().size() >= nextBuild.viceCount) {
            return Response.VICE_MAX;
        }
        // 设置副会长
        gang.getAdmins().add(viceId);
        member.setPosition(Gang.VICE_ADMIN);
        // 发送邮件
        String mail = ConfigData.getConfig(ErrCode.class, Response.YOU_BE_VICE).tips;
        mailService.sendSysMail(mail, mail, null, viceId, null);

        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                getMyGang(viceId), viceId);

        gang.setUpdated(true);

        return Response.SUCCESS;
    }

    // 解散
    public int dissolve(int playerId) {
        // 验证是否有权限
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (playerId != gang.getOwnerId()) {
            return Response.GANG_NO_PRIVILEGE;// 权限不够
        }
        // 只有一个人才解散
        if (gang.getMembers().size() > 1) {
            return Response.DISSOVLE_MORE_ONE;// 无法解散
        }

        stopTraining(playerId);

        // 删除帮派数据
        gangs.remove(gangId);

        String key = sceneService.getGroupKey(player);
        SessionManager.getInstance().removeFromGroup(key, player.getPlayerId());
        player.setGangId(0);
        gangDao.del(gangId);
        orderGangs.remove(gang);
        sort(false);
        calculator.calculate(player);

        PlayerData data = playerService.getPlayerData(playerId);
        data.setLastQuitGang(System.currentTimeMillis());
//		SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
//				getMyGang(playerId), playerId);
        return Response.SUCCESS;
    }

    // 更新公告
    public int udpateNotice(int playerId, String notice) {
        // 验证是否有权限
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (!isAdmin(playerId, gang)) {
            return Response.GANG_NO_PRIVILEGE;
        }
        gang.setNotice(notice);
        gang.setUpdated(true);
        return Response.SUCCESS;
    }

    // 捐献
    public int donate(int playerId, int index) {
        Player player = playerService.getPlayer(playerId);
        Gang gang = gangs.get(player.getGangId());
        GMember member = gang.getMembers().get(playerId);

        int[] param = ConfigData.DonateCfg.get(index);// [货币,货币数量,贡献,次数]

        // 检验次数
        int time = member.getDonationRecord().getOrDefault(index, 0);
        /*if (time >= param[3]) {
            return Response.NO_TODAY_TIMES;
        }*/
        // 计算价值
        if (goodsService.decConsume(playerId,
                new int[][]{Arrays.copyOfRange(param, 0, 2)},
                LogConsume.GANG_DONATE, index) > 0) {
            return Response.NO_CURRENCY;
        }

        //3种捐献共享次数
        ++time;
        member.getDonationRecord().put(1, time);
        member.getDonationRecord().put(2, time);
        member.getDonationRecord().put(3, time);

        int contribute = param[2];
        addContribute(playerId, contribute);

        // 更新
        gang.setUpdated(true);

        taskService.doTask(playerId, Task.FINISH_DONATE, index, 1);

        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG, getMyGang(playerId), playerId);

        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_DONATION_INFO, getDonationInfo(playerId), playerId);

        //公会捐献活动
        activityService.tour(playerId, ActivityConsts.ActivityTaskCondType.T_GUILD_DONATION);

        return Response.SUCCESS;
    }

    // 设置条件
    public int setLimit(int playerId, GangLimit limit) {
        // 验证是否有权限
        Player player = playerService.getPlayer(playerId);
        int gangId = player.getGangId();
        Gang gang = gangs.get(gangId);
        if (!isAdmin(playerId, gang)) {
            return Response.GANG_NO_PRIVILEGE;
        }
        // 更新字段
        gang.setAutoJoin(limit.autoJoin);
        gang.setLimitLev(limit.levLimit);
        gang.setLevLimit(limit.level);
        gang.setLimitFight(limit.fightLimit);
        gang.setFightLimit(limit.fihgt);
        gang.setUpdated(true);

        // 推送给会长和管理员
        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                getMyGang(gang.getOwnerId()), gang.getOwnerId());
        for (int vice : gang.getAdmins()) {
            SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                    getMyGang(vice), vice);
        }
        // 自动处理那些审批
        if (gang.getAutoJoin()) {
            for (int applyId : gang.getApplys().keySet()) {
                Player cache = playerService.getPlayer(applyId);
                // 自动审批
                if (cache.getLev() >= gang.getLevLimit()
                        && cache.getFight() >= gang.getFightLimit()) {
                    approve(playerId, applyId);
                } else {// 自动拒绝
                    refuse(playerId, applyId);
                }
            }
        }
        gang.setUpdated(true);
        return Response.SUCCESS;
    }

    public void addContribute(int playerId, int contribute) {
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null) {
            return;
        }
        playerService.addCurrency(playerId, Goods.CONTRIBUTE, contribute,
                LogConsume.GANG_DONATE);
        int gDonate = contribute * ConfigData.globalParam().person2gang / 100;
        // 更新公会资产da
        gang.setUpdated(true);
        gang.alterAsset(gDonate);
        gang.alterTotalAsset(gDonate);
        gang.getMembers().get(playerId).alterContribute7(gDonate);
        int nextLev = Gang.MAIN_BUILD * 100 + gang.getLev() + 1;
        GangBuildCfg nextBuild = ConfigData.getConfig(GangBuildCfg.class,
                nextLev);
        if (nextBuild != null && gang.getTotalAsset() >= nextBuild.levRequest) {
            gang.setLev(nextBuild.lev);

            gang.setMaxNum(nextBuild.memberCount);
            SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                    getMyGang(playerId), playerId);

            Map<Integer, int[]> condParam = Maps.newHashMap();
            condParam.put(Task.TYPE_GANG_TEC, new int[]{gang.getLev()});
            condParam.put(Task.TYPE_GANG_LEVEL, new int[]{gang.getLev()});
            for (int receiveId : gang.getMembers().keySet()) {
                PlayerView playerView = serialService.getData().getPlayerView(receiveId);
                playerView.setGangMaxLevel(gang.getLev());

                taskService.doTask(receiveId, condParam);
            }

            //公会等级活动
            activityService.tour(playerId, ActivityConsts.ActivityTaskCondType.T_GUILD_GRADE, gang.getLev());
        }

        gang.setUpdated(true);
    }

    // 自动替换会长
    private void checkUpdateOwner(Gang g) {
        // 判断会长两周以上未登录d
        Player owner = playerService.getPlayer(g.getOwnerId());
        if (owner == null) {
            return;
        }
        long passTime = (System.currentTimeMillis() - owner.getLastLoginTime()
                .getTime()) / (TimeUtil.ONE_HOUR * 24);
        if (passTime < 14) {
            return;
        }
        //管理员
        int newId = selectLeader(g, true);
        if (newId == 0) { //成员筛选
            newId = selectLeader(g, false);
        }
        if (newId == 0) {
            return;
        }

        if (!g.isContribute()) {
            int lev = g.getLev();
            GMember member = g.getMembers().get(g.getOwnerId());
            if (member.getContribute7() < ConfigData.globalParam().gangCompensate) {
                lev = 1;
            }
            //发补偿
            String title = ConfigData.getConfig(ErrCode.class,
                    Response.TRANSFORM_GANG_COMPENSATE_TITLE).tips;
            String content = ConfigData.getConfig(ErrCode.class,
                    Response.TRANSFORM_GANG_COMPENSATE_CONTENT).tips;
            mailService.sendSysMail(title, content,
                    Arrays.asList(new GoodsEntry(Goods.DIAMOND, lev * ConfigData.globalParam().createGangDiamond)), g.getOwnerId(), null);
            g.setContribute(true);
            g.setUpdated(true);
        }
        ServerLogger.info("#checkUpdateOwner change leader,leader id = " + newId);
        // 更新数据
        transfer(g.getOwnerId(), newId);

    }

    /**
     * 筛选14日以内登录的
     *
     * @param g
     * @param bAdmin
     * @return
     */
    private int selectLeader(Gang g, boolean bAdmin) {
        Collection<Integer> ids = g.getAdmins();
        if (!bAdmin) {
            ids = g.getMembers().keySet();
        }
        int newId = 0;
        int contribute = 0;
        long passTime;
        for (int id : ids) {
            if (id == newId) {
                continue;
            }
            Player adminPlayer = playerService.getPlayer(id);
            passTime = (System.currentTimeMillis() - adminPlayer.getLastLoginTime()
                    .getTime()) / (TimeUtil.ONE_HOUR * 24);
            if (passTime >= 14) {
                continue;
            }
            GMember member = g.getMembers().get(id);
            if (member.getContribute7() > contribute) {
                contribute = member.getContribute7();
                newId = id;
            } else if (member.getContribute7() == contribute) {
                if (newId == 0) {
                    newId = id;
                } else {
                    Player newPlayer = playerService.getPlayer(newId);
                    if (newPlayer == null) {
                        ServerLogger.info("newId ==" + newId);
                        newId = id;
                        continue;
                    }
                    if (adminPlayer.getFight() > newPlayer.getFight()) {
                        newId = id;
                    }
                }
            }
        }
        return newId;
    }

    // 每日维护
    public void daily() {
        for (Gang g : gangs.values()) {
            // 计算钱够不够
            int lev = g.getLev();
            // 旧数据容错处理
            if (lev == 0) {
                g.setLev(1);
                lev = 1;
            }
            long day = (System.currentTimeMillis() - g.getCreateDate().getTimeInMillis()) / (TimeUtil.ONE_DAY) + 1;
            boolean refresh = day % 7 == 0;
            for (GMember member : g.getMembers().values()) {
                member.getDonationRecord().clear();
                member.setChallengeTimes(ConfigData.globalParam().guildCopyTimes);
                if (refresh) {
                    member.setContribute7(0);
                }
            }
            synchronized (g) {
                // 自动转让会长
                checkUpdateOwner(g);
            }
            g.setUpdated(true);
        }
    }

    // 更新数据
    public void update() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (Gang g : gangs.values()) {
            if (g.isUpdated()) {
                // db
                String str = JsonUtils.object2String(g);
                byte[] dbData = str.getBytes(Charset.forName("utf-8"));
                params.add(new Object[]{g.getId(), CompressUtil.compressBytes(dbData)});
                g.setUpdated(false);
            }
        }
        Context.batchDb(GangDao.UPDATE, params);
    }

    public void sort() {
        sort(true);
    }

    public void sort(boolean refresh) {
        if (refresh) {
            for (Gang g : gangs.values()) {
                g.refreshFight();
            }
        }
        orderGangs = new ArrayList<>(gangs.values());
        orderGangs.sort(comparator);
        int rank = 1;
        for (Gang g : orderGangs) {
            for (int id : g.getMembers().keySet()) {
                if (serialService.getData() != null) {
                    taskService.doTask(id, Task.TYPE_GANG_RANK, rank);
                    PlayerView playerView = serialService.getData().getPlayerView(id);
                    if (playerView.getGangMaxRank() > rank) {
                        playerView.setGangMaxRank(rank);
                    }
                }
            }

            g.setRank(rank++);
        }
    }


    public Comparator<Gang> comparator = new Comparator<Gang>() {
        @Override
        public int compare(Gang g1, Gang g2) {
            return g2.getTotalFight() - g1.getTotalFight();
        }
    };

    public int rename(int playerId, String newName) {
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null || gang.getOwnerId() != playerId) {
            return Response.GANG_NO_PRIVILEGE;
        }
        if (newName.equals(gang.getName()) || newName.length() < 2 || newName.length() > 6) {
            return Response.ERR_PARAM;
        }
        // 同名
        if (gangNames.get(newName) != null) {
            return Response.GANG_SAME_NAME;
        }
        // 验证钻石
        if (!playerService.decDiamond(playerId, ConfigData.globalParam().renameGang,
                LogConsume.GANG_RENAME)) {
            return Response.NO_DIAMOND;
        }
        gang.setName(newName);
        gang.setUpdated(true);
        return Response.SUCCESS;
    }

    public int brocast(int playerId, String content) {
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null || !isAdmin(playerId, gang)) {
            return Response.GANG_NO_PRIVILEGE;
        }
        if (content.length() > 56) {
            return Response.ERR_PARAM;
        }
        // 验证钻石
        if (!playerService.decDiamond(playerId, ConfigData.globalParam().gangBrocast,
                LogConsume.GANG_BROCAST)) {
            return Response.NO_DIAMOND;
        }
        ChatVo vo = new ChatVo();
        vo.content = content;
        vo.channel = ChatExtension.PRIVATE;
        vo.sender = player.getName();//防外挂
        vo.senderId = player.getPlayerId();
        vo.senderVip = player.getVip();

        ListParam<ChatVo> result = new ListParam<ChatVo>();
        result.params = new ArrayList<ChatVo>();
        result.params.add(vo);
        for (int receiveId : gang.getMembers().keySet()) {
            if (receiveId == playerId) continue;
            if (!SessionManager.getInstance().isActive(receiveId)) {
                vo.time = System.currentTimeMillis();
                chatService.addOffChat(receiveId, vo);
            } else {
                SessionManager.getInstance().sendMsg(ChatExtension.CHAT, result, receiveId);
            }
        }
        return Response.SUCCESS;
    }

    public ListParam<Int2Param> getDonationInfo(int playerId) {
        ListParam<Int2Param> result = new ListParam<Int2Param>();
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null) {
            result.code = Response.ERR_PARAM;
            return result;
        }
        GMember member = gang.getMembers().get(playerId);
        if (member == null) {
            result.code = Response.ERR_PARAM;
            return result;
        }
        result.params = new ArrayList<Int2Param>();
        for (Map.Entry<Integer, Integer> entry : member.getDonationRecord().entrySet()) {
            Int2Param param = new Int2Param();
            param.param1 = entry.getKey();
            param.param2 = entry.getValue();
            result.params.add(param);
        }
        return result;
    }

    public void sendTaskReward(int gangId, TaskConfig config) {
        Set<Integer> memberIds = getGang(gangId).getMembers().keySet();
        String title = GameData.getConfig(ErrCode.class, Response.GANG_TASK_TITLE).tips;
        String content = MessageFormat.format(GameData.getConfig(ErrCode.class, Response.GANG_TASK_TITLE).tips, config.taskName);
        for (int memberId : memberIds) {
            mailService.sendSysMailRewards(title, content, config.rewards, memberId, LogConsume.TASK_REWARD);
        }
    }

    public int launchGTRoom(int playerId, int roomId) {
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null || !isAdmin(playerId, gang)) {
            return Response.GANG_NO_PRIVILEGE;
        }

        if (gang.getGtRoom() != null) {
            return Response.GANG_DUPLICATE_GTROOM;
        }

        GangTrainingCfg cfg = GameData.getConfig(GangTrainingCfg.class, roomId);
        if (cfg == null) {
            return Response.ERR_PARAM;
        }
        if (cfg.reqLev > gang.getLev()) {
            return Response.NO_LEV;
        }
        if (cfg.assetConsume > gang.getAsset()) {
            return Response.NO_GANG_ASSET;
        }
        if (cfg.itemConsume != null) {
            int respCode = goodsService.decConsume(playerId, cfg.itemConsume, LogConsume.GANG_BROCAST);
            if (respCode > 0) {
                return respCode;
            }
        }
        gang.alterAsset(-cfg.assetConsume);
        GTRoom room = new GTRoom(roomId);
        gang.setGtRoom(room);
        CloseTask task = new CloseTask(gang);
        Future<?> future = Context.getTimerService().scheduleDelay(task, cfg.maxTime, TimeUnit.MINUTES);
        task.future = future;
        room.setCloseTask(task);
        gang.setUpdated(true);
        return Response.SUCCESS;
    }

    public int startTraining(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null) {
            return Response.ERR_PARAM;
        }
        GMember member = gang.getMembers().get(playerId);
        if (member.getStartTraining() > 0) {
            return Response.GANG_DUPLICATE_TRAINING;
        }
        GTRoom room = gang.getGtRoom();
        if (room == null) {
            return Response.NO_GANG_TRAINING;
        }
        //GangTrainingCfg cfg = GameData.getConfig(GangTrainingCfg.class, room.getId());
        member.setStartTraining(System.currentTimeMillis());
        if (member.getTrainingTime() == 0) {
            room.addMax();
        }
        gang.setUpdated(true);

        //公会宴会活动
        activityService.tour(playerId, ActivityConsts.ActivityTaskCondType.T_GUILD_BANQUET);

        return Response.SUCCESS;
    }

    public ListParam<Reward> takeTrainingReward(int playerId) {
        ListParam<Reward> result = new ListParam<>();
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null) {
            result.code = Response.ERR_PARAM;
            return result;
        }
        GMember member = gang.getMembers().get(playerId);
        if (member.getStartTraining() == 0) { //还未开始
            result.code = Response.ERR_PARAM;
            return result;
        }
        GTRoom room = gang.getGtRoom();
        if (room == null) {
            result.code = Response.ERR_PARAM;
            return result;
        }

        if (member.getTrainingTime() == 0) {
            room.setMax(room.getMax() - 1);
        }

        GangTrainingCfg cfg = GameData.getConfig(GangTrainingCfg.class, room.getId());
        float plus = Math.min(room.getMax() * cfg.rewardPlus[1], cfg.rewardPlus[0]);
        int hour = (int) member.getTrainingTime();
        int[][] rewards = calculateReward(member, room, plus);
        member.setStartTraining(0L);
        if (rewards != null) {
            result.params = new ArrayList<>();
            goodsService.addRewards(playerId, rewards, LogConsume.GANG_TRAINING_REWARD, room.getId(), member.getTrainingTime() - hour);
            for (int[] reward : rewards) {
                Reward re = new Reward();
                re.id = reward[0];
                re.count = reward[1];
                result.params.add(re);
            }
        }
        gang.setUpdated(true);
        return result;
    }

    public int stopTraining(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null || !isAdmin(playerId, gang)) {
            return Response.GANG_NO_PRIVILEGE;
        }
        GTRoom room = gang.getGtRoom();
        if (room == null) {
            return Response.NO_GANG_TRAINING;
        }
        room.getCloseTask().cancel();
        room.getCloseTask().run();
        gang.setUpdated(true);
        return Response.SUCCESS;
    }

    private void sendTrainingReward(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null) {
            return;
        }
        GTRoom room = gang.getGtRoom();
        if (room == null) {
            return;
        }
        GMember member = gang.getMembers().get(playerId);
        GangTrainingCfg cfg = GameData.getConfig(GangTrainingCfg.class, room.getId());
        float plus = Math.min(room.getMax() * cfg.rewardPlus[1], cfg.rewardPlus[0]);

        if (member.getStartTraining() == 0) {
            return;
        }
        int[][] rewards = BeanManager.getBean(GangService.class).calculateReward(member, room, plus);
        if (rewards == null) {
            return;
        }
        gang.setUpdated(true);
        // 发送邮件
        String title = ConfigData.getConfig(ErrCode.class,
                Response.GANG_TRAINING_TITLE).tips;
        String content = ConfigData.getConfig(ErrCode.class,
                Response.GANG_TRAINING_CONTENT).tips;
        BeanManager.getBean(MailService.class).sendSysMailRewards(title, content, rewards, member.getPlayerId(), LogConsume.GANG_TRAINING_REWARD);
    }


    public int[][] calculateReward(GMember member, GTRoom room, float plus) {
        try {
            GangTrainingCfg cfg = GameData.getConfig(GangTrainingCfg.class, room.getId());
            long passHour = ((System.currentTimeMillis() - member.getStartTraining()) / TimeUtil.ONE_HOUR);
            int hour = (int) passHour;
            if (hour < 1) { //小于1小时，不给奖励
                member.alterTrainingTime(passHour);
                return null;
            }

            int max = (int) ((cfg.maxTime - (member.getStartTraining() - room.getCreateTime()) / TimeUtil.ONE_MIN) / 60);
            if (hour > max) {
                hour = max;
            }

            if (hour + (int) member.getTrainingTime() > cfg.validTime) {
                hour = cfg.validTime - (int) member.getTrainingTime();
            }

            member.alterTrainingTime(hour);

            int[][] rewardArr = new int[cfg.reward.length][];
            int i = 0;
            for (int[] reward : cfg.reward) {
                int[] arr = new int[2];
                arr[0] = reward[0];
                arr[1] = (int) (reward[1] * plus * hour);
                rewardArr[i++] = arr;
            }

            return rewardArr;
        } catch (Exception e) {
            ServerLogger.err(e, "宴会bug");
        }

        return null;
    }


    public static class CloseTask implements Runnable {

        private Gang gang;
        private Future<?> future;

        public CloseTask(Gang gang) {
            this.gang = gang;
        }

        @Override
        public void run() {
            if (gang == null) {
                return;
            }
            GTRoom room = gang.getGtRoom();
            if (room == null) {
                return;
            }
            GangTrainingCfg cfg = GameData.getConfig(GangTrainingCfg.class, room.getId());
            gang.setGtRoom(null);
            float plus = Math.min(room.getMax() * cfg.rewardPlus[1], cfg.rewardPlus[0]);
            // 发送邮件
            String title = ConfigData.getConfig(ErrCode.class, Response.GANG_TRAINING_TITLE).tips;
            String content = ConfigData.getConfig(ErrCode.class, Response.GANG_TRAINING_CONTENT).tips;
            for (GMember member : gang.getMembers().values()) {
                if (member.getStartTraining() != 0) {
                    int[][] rewards = BeanManager.getBean(GangService.class).calculateReward(member, room, plus);
                    if (rewards != null) {
                        BeanManager.getBean(MailService.class).sendSysMailRewards(title, content, rewards, member.getPlayerId(), LogConsume.GANG_TRAINING_REWARD);
                    }
                }
                member.setTrainingTime(0);
                member.setStartTraining(0L);
            }
            gang.setUpdated(true);
        }

        public void cancel() {
            if (future.isDone() || future.isCancelled()) {
                return;
            }
            future.cancel(true);
        }
    }


    /**
     * 解锁
     *
     * @param playerId
     * @param technologyId
     * @return
     */
    public Int2Param unLockTechnology(int playerId, int technologyId) {
        Int2Param ret = new Int2Param();
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null) {
            ret.param1 = Response.ERR_PARAM;
            return ret;
        }
        GMember member = gang.getMembers().get(playerId);
        if (member.getPosition() == Gang.MEMBER) {
            ret.param1 = Response.GANG_NO_PRIVILEGE;
            return ret;
        }

        GangScienceCfg conf = ConfigData.getConfig(GangScienceCfg.class, technologyId);
        if (conf.lv != 0 || gang.getLev() < conf.NeedLevel) {
            ret.param1 = Response.GUILD_LEVEL_LIMIT;
            return ret;
        }

        if (gang.getAsset() < conf.Price) {
            ret.param1 = Response.NO_GANG_ASSET;
            return ret;
        }

        if (gang.getTechnologys().contains(technologyId)) {
            ret.param1 = Response.ERR_PARAM;
            return ret;
        }

        taskService.doTask(playerId, Task.TYPE_GANG_TEC, conf.NeedLevel);
        gang.setAsset(gang.getAsset() - conf.Price);
        gang.getTechnologys().add(technologyId);
        ret.param1 = Response.SUCCESS;
        ret.param2 = technologyId;
        gang.setUpdated(true);
        return ret;
    }

    /**
     * 升级
     *
     * @param playerId
     * @param technologyId
     * @return
     */
    public IntParam learnTechnology(int playerId, int technologyId) {
        IntParam ret = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        if (gang == null) {
            ret.param = Response.GUILD_NOT_EXIST;
            return ret;
        }

        GangScienceCfg conf = ConfigData.getConfig(GangScienceCfg.class, technologyId);
        int initTechnologyId = ConfigData.guildTechnology.get(conf.type + "_" + conf.NeedLevel);
        if (!gang.getTechnologys().contains(initTechnologyId)) { //是否解锁
            ret.param = Response.GUILD_SKILL_UNLOCK;
            return ret;
        }

        if (conf.NextID == 0) { //最大等级了
            ret.param = Response.GUILD_SKILL_MAX_LV;
            return ret;
        }

        PlayerData data = playerService.getPlayerData(playerId);
        if (initTechnologyId != technologyId && !data.getTechnologys().contains(technologyId)) { //该技能没有学习
            ret.param = Response.GUILD_SKILL_UNLOCK;
            return ret;
        }

        GangScienceCfg nextConf = ConfigData.getConfig(GangScienceCfg.class, conf.NextID);
        List<GoodsEntry> goodsList = Lists.newArrayList(new GoodsEntry(Goods.CONTRIBUTE, nextConf.Price));
        if (Response.SUCCESS != goodsService.decConsume(playerId, goodsList, LogConsume.GUILD_OPEN_BOSS)) {
            ret.param = Response.NO_GANG_ASSET;
            return ret;
        }

        data.getTechnologys().remove(technologyId);
        data.getTechnologys().add(conf.NextID);

        calculator.calculate(player);
        ret.param = Response.SUCCESS;

        gang.setUpdated(true);

        //公会科技活动
        PlayerData playerData = playerService.getPlayerData(playerId);
        if (playerData != null) {
            if (activityService.checkIsOpen(playerData, ActivityConsts.ActivityTaskCondType.T_GUILD_TECHNOLOGY)) {
                Map<Integer, Integer> typeNumberMap = getTypeNumberMap(playerId);
                if (typeNumberMap != null && !typeNumberMap.isEmpty()) {
                    activityService.completeActivityTask(playerId, ActivityConsts.ActivityTaskCondType.T_GUILD_TECHNOLOGY, true, typeNumberMap, true);
                }
            }
        } else {
            ServerLogger.warn("玩家数据不存在，玩家id=" + playerId);
        }

        return ret;
    }

    /**
     * 获取学习的科技列表
     *
     * @param playerId
     * @return
     */
    public IntList getTechnology(int playerId) {
        IntList list = new IntList();
        list.iList = new ArrayList<>();

        PlayerData data = playerService.getPlayerData(playerId);
        list.iList.addAll(data.getTechnologys());

        return list;
    }

    public void clearGuildTime(int playerId) {
        PlayerData playerData = playerService.getPlayerData(playerId);
        playerData.setLastQuitGang(0);
    }

    public void addGangPoint(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Gang gang = getGang(player.getGangId());
        gang.setAsset(10000000);
        gang.setTotalAsset(10000000);
        gang.setUpdated(true);
        gang.setLev(10);
        SessionManager.getInstance().sendMsg(GangExtension.REFRESH_GANG,
                getMyGang(playerId), playerId);
    }

    /**
     * 公会科技等级次数
     *
     * @param playerId 玩家id
     * @return 公会科技等级次数
     */
    private Map<Integer, Integer> getTypeNumberMap(int playerId) {
        Map<Integer, Integer> technologyMap = new ConcurrentHashMap<>();
        PlayerData data = playerService.getPlayerData(playerId);
        if (data == null) {
            ServerLogger.warn("玩家数据不存在，玩家id=" + playerId);
            return technologyMap;
        }

        Set<Integer> technologys = data.getTechnologys();
        if (technologys.isEmpty()) {
            ServerLogger.info("未解锁任何公会科技");
            return technologyMap;
        }

        for (Integer id : technologys) {
            GangScienceCfg gangScienceCfg = ConfigData.getConfig(GangScienceCfg.class, id);
            if (gangScienceCfg != null && gangScienceCfg.lv >= 1) {
                for (int i = 1; i <= gangScienceCfg.lv; i++) {
                    if (technologyMap.get(i) == null) {
                        technologyMap.put(i, 1);
                    } else {
                        technologyMap.put(i, technologyMap.get(i) + 1);
                    }
                }
            }
        }

        return technologyMap;
    }
}
