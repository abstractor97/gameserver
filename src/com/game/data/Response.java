package com.game.data;
/**
 * 错误码(工具生成，勿手动修改)
 */
public class Response {
	//系统
	public static final int SUCCESS=0;//成功
	public static final int NO_DIAMOND=1;//钻石不足
	public static final int NO_COIN=2;//金币不足
	public static final int BAG_FULL=3;//背包已满
	public static final int SYS_ERR=4;//系统错误
	public static final int NO_ENERGY=5;//体力不足
	public static final int ERR_PARAM=6;//参数错误,非法请求
	public static final int NO_VIP=7;//VIP等级不足
	public static final int NO_LEV=10;//等级不足
	public static final int SYS_NUM_ERR=11;//系统检测到异常行为1，即将断开连接，请联系客服
	public static final int SYS_KEY_ERR=12;//系统检测到异常行为2，即将断开连接，请联系客服
	public static final int SYS_HEART_ERR=13;//系统检测到异常行为3，即将断开连接，请联系客服
	public static final int NO_CONTRIBUTE=14;//帮贡不足
	public static final int NO_TODAY_TIMES=18;//今天次数已经用完
	public static final int NO_ACTIVE_VAL=19;//活跃值不足
	public static final int NO_OPEN=20;//游戏处于维护中，请稍后再登陆
	public static final int LOW_VERSION=22;//版本太低，请到所在应用商城下载最新版本
	public static final int CLOSE_REG=23;//本服已经关闭注册新角色，请前往新服
	public static final int NO_MATERIAL=24;//材料不足
	public static final int EXCEED_LEV=26;//不能超过角色等级
	public static final int SOLD_OUT=27;//已经售罄
	public static final int NO_ENDLESS_COIN=28;//旋涡币不足
	public static final int NO_MEDAL_COIN=29;//勋章不足
	public static final int NO_TRAINING_COIN=30;//试炼币不足
	public static final int NO_LOTTERY_TICKET=31;//补给卷不足
	public static final int NO_FAME=32;//声望不足
	public static final int SYS=99;//系统
	public static final int WELCOME_MAIL_TITLE=100;//欢迎您来到《我守护的世界》
	public static final int WELCOME_MAIL_CONTENT=101;//尊敬的玩家，感谢您对游戏的支持！有问题可以随时联系我们
	public static final int RE_LOGIN=102;//重复登录
	public static final int Max_Number=103;//服务器达到最大人数
	//角色系统
	public static final int SAME_NAME=1001;//该名字已被使用
	public static final int NO_USER=1002;//不存在的角色
	public static final int TOO_MANY_CON=1003;//在线用户太多，请稍后再试
	public static final int TOO_MANY_ROLE=1004;//角色数量超过上限
	public static final int BUY_ENERGY_EXCEED=1005;//今日购买体力次数已达上限，升级vip可增加次数
	public static final int ERR_NAME=1006;//名字包含非法字符
	//装备
	public static final int MAX_STAR=1201;//星级已满
	public static final int MAX_STRENGTH=1202;//强化等级已满
	public static final int MAX_LEV=1203;//已经是最高级了
	public static final int STRENGTH_FAIL=1204;//强化失败，返还50%材料
	public static final int NO_EQUIPPARTSUP=1205;//升星失败
	//任务系统
	public static final int TASK_NOT_FINISHED=1301;//任务尚未完成
	public static final int TASK_JOINTED=1302;//对方已接取协助任务
	public static final int TASK_PERFORMING=1303;//该邀请已被其他玩家抢先接取
	public static final int TASK_NO_NOINT=1304;//对方已完成3个协助任务，不能再邀请
	public static final int TASK_INVALID=1305;//合作任务已失效
	public static final int PARTNER_NO_LEV=1306;//对方等级不足,不能接受此任务
	//邮件
	public static final int NO_MAIL=1401;//邮件不存在
	public static final int FULL_BAG_TITLE=1402;//您的背包已满
	public static final int FULL_BAG_CONTENT=1403;//您的背包已满，请尽快清理背包
	public static final int ERR_GOODS_TYPE=1404;//物品类型错误
	public static final int NO_VOCATION=1405;//职业不符合
	public static final int COPY_NO_PRE=1406;//前置关卡未完成
	//好友
	public static final int MY_MAX_FRIENDS=1501;//好友数量已达上限
	public static final int HE_MAX_FRIENDS=1502;//对方好友已满
	public static final int NO_FRIEND_INFO=1503;//没有找到该玩家信息
	public static final int IN_BLACK_LIST=1504;//你已被对方拉黑
	public static final int IS_FRIEND=1505;//对方已是你的好友
	public static final int NO_ADD_YOURSELF=1506;//不可以添加自己为好友
	public static final int ERR_GOODS_COUNT=1601;//物品数量不足
	public static final int SHOP_HAS_REFRESH=1701;//商城已经刷新了，请重试
	//公会
	public static final int GANG_APPLY_MAX=2501;//今日申请公会次数已达上限
	public static final int HAS_GANG=2502;//您已经加入公会
	public static final int GANG_APPLY_LIMIT=2503;//您的战力和等级不足，请试试其他公会吧。
	public static final int GANG_NO_PRIVILEGE=2504;//您的权限不足
	public static final int APPLY_OUTDATED=2505;//申请已失效
	public static final int GANG_FULL=2506;//公会成员已满
	public static final int JOIN_GANG=2507;//%s加入公会
	public static final int JOIN_SUCESS_TITLE=2508;//申请公会成功
	public static final int JOIN_SUCESS_CONTENT=2509;//您已成功加入-%s,去和大家打个招呼吧！
	public static final int EXIT_GANG=2510;//%s离开公会
	public static final int KICK_GANG=2511;//您已经被请出%s
	public static final int TRANSFER_OWNER=2512;//不能转让给盟主本人
	public static final int NEW_OWNER=2513;//%s万人拥护，众望所归，成为公会新盟主
	public static final int BE_NEW_OWNER=2514;//恭喜您成为盟主
	public static final int SET_POS_OWNER=2515;//不能对盟主进行该操作
	public static final int DISSOVLE_MORE_ONE=2517;//您只能解散单人的公会。或选择转让给其他玩家
	public static final int SB_BE_VICE=2518;//%s成为公会副盟主
	public static final int YOU_BE_VICE=2519;//恭喜您成为副盟主
	public static final int VICE_MAX=2520;//公会副盟主人数已满
	public static final int GANG_DONATE=2521;//%s捐赠%d金币
	public static final int GANG_SAME_NAME=2522;//您所创建公会名称已存在，请再次尝试。
	public static final int TRANSFER_VICE=2523;//转让给公会副盟主
	public static final int MAIN_BUILD_LESS=2524;//请先升级主建筑
	public static final int OTHER_BUILD_LESS=2525;//您还有其他建筑未升级
	public static final int BUILD_FULL=2526;//您的建筑已满级
	public static final int GANG_COIN_LESS=2527;//公会资金不足，无法使用该使用该功能
	public static final int DEC_MAIN_TAIN=2528;//扣除公会维护资金%d金币
	public static final int UPGRADE_MAIN=2529;//主建筑升到%d级
	public static final int TRANSFORM_GANG_COMPENSATE_TITLE=2530;//转让公会补偿邮件标题
	public static final int TRANSFORM_GANG_COMPENSATE_CONTENT=2531;//转让公会补偿邮件内容
	public static final int GANG_HAS_APPLY=2550;//您已经提交申请，请等待结果
	public static final int GANG_APPLY_MEMBER_MAX=2551;//该公会申请人数已满，请稍后再试
	public static final int GANG_TRANSFER_TITLE=2552;//帮主转让通知
	public static final int GANG_TRANSFER_CONTENT=2553;//%s已经成为新一任的帮主,带领大家踏上征途！
	public static final int QUIT_GANG_LAST=2554;//退出公会24小时内无法加入新公会，请稍后再试
	public static final int HAS_TAKE_REWARD=2555;//已经领取过奖励了
	public static final int NO_CURRENCY=2556;//货币不足，无法使用该功能
	public static final int GANG_TASK_TITLE=2561;//完成公会任务
	public static final int GANG_TASK_CONTENT=2562;//完成公会任务[{0}]
	public static final int NO_TRAVERSING_ENERGY=2563;//穿越仪能量不足
	//时装
	public static final int DUPLICATE_FASHION_TITLE=3701;//重复时装补偿
	public static final int FASHION_TIME_OUT=3710;//时装已经过期
	public static final int DUPLICATE_FASHION_CONTENT=3702;//尊敬的玩家，由于你已经拥有时装：%s 了，不能重复获得，现在补充对应的钻石给你！
	public static final int GANG_DUPLICATE_GTROOM=3703;//公会练功房已开启,不能重复开启
	public static final int NO_GANG_ASSET=3704;//公会资金不足
	public static final int NO_GANG_TRAINING=3705;//宴会暂时未开启
	public static final int GANG_TRAINING_TITLE=3706;//宴会奖励标题
	public static final int GANG_TRAINING_CONTENT=3707;//宴会奖励内容
	public static final int GANG_DUPLICATE_TRAINING=3708;//你已经开始练功,不能重复开始
	public static final int NO_GANG_TRAINING_REWARD=3709;//有效时长已用完,将不再获得收益
	//组队
	public static final int NO_ALL_READY=3802;//当前有队员未准备，无法进入副本
	public static final int MEMBER_NO_ENERGY=3803;//{0}的不符合进入要求，无法进入。
	public static final int TEAM_FULL=3805;//当前队伍已满，无法加入队伍。
	public static final int NO_TEAM=3806;//当前队伍不存在，无法加入队伍。
	public static final int TEAM_RUNNING_NO_JOIN=3807;//当前队伍已在副本中,无法加入队伍
	public static final int TEAM_RUNNING_NO_DISSOLVE=3808;//当前队伍已在副本中,无法解散队伍
	public static final int TEAM_RUNNING_NO_LEAVE=3809;//当前队伍已在副本中,无法离开队伍
	public static final int TEAM_NO_READY=3810;//还有队员未准备好
	//签到
	public static final int SIGN_HAS_DONE=3851;//已经签到过
	public static final int TEAM_NO_EXIT=3852;//组队中不能进入其它战斗!
	public static final int IN_TEAMING=3853;//你已在队伍中,不能再加入别的队伍
	//世界BOSS
	public static final int WORLD_BOSS_END=3901;//活动已结束
	public static final int WORLD_BOSS_KILLED=3902;//挑战的BOSS已经死亡
	public static final int ARTIFACT_SAME_PART=3903;//激活相同部件,返还50%材料
	public static final int WORLD_BOSS_LAST_BEAT_TITLE=3904;//最后一击奖励
	public static final int WORLD_BOSS_LAST_BEAT_CONTENT=3905;//最后一击奖励
	public static final int WORLD_BOSS_RANK_TITLE=3906;//排名奖励
	public static final int WORLD_BOSS_RANK_CONTENT=3907;//排名奖励
	public static final int WORLD_BOSS_KILL_TITLE=3908;//击杀奖励
	public static final int WORLD_BOSS_KILL_CONTENT=3909;//击杀奖励
	public static final int WORLD_BOSS_NOT_BUY_TIMES=3910;//购买次数已满
	public static final int ARENA_NO_CHELLENGE=4001;//挑战次数不足
	public static final int ARENA_NO_BUY=4002;//今天购买次数已用完
	//团队副本
	public static final int TEAM_NO_LEVEL=4101;//当前等级未达到{0}级，无法进入星陨秘境！
	public static final int TEAM_PEOPLE_REACHED=4102;//当前团队人数已满，无法加入。
	public static final int TEAM_NO_OPEN=4103;//当前团队已关闭招收权限，无法加入。
	public static final int TEAM_NULL=4104;//当前团队已解散，无法执行该操作。
	public static final int TEAM_TIME_OVER=4105;//当前活动已关闭，无法执行该操作。
	public static final int TEAM_NO_NUM=4106;//你已参加过活动，请明日再来。
	public static final int TEAM_TURE=4107;//你已拥有一个团队，无法加入新的团队。
	public static final int TEAM_NO_LEVEL_QI=4108;//未达到该秘境的等级要求，无法选择该秘境。
	public static final int TEAM_NO_OK=4109;//当前小队有队员未准备，无法进入副本.
	public static final int TEAM_NO_NUM_FALSE=4110;//{0}XXX（玩家名字，有多个则显示多个。）的进入次数不足，无法进入副本。’
	public static final int TEAM_NO_REVISE=4111;//当前小队人数已满，无法调整到该队伍。
	public static final int TEAM_ACCOMPLISH=4112;//当前副本已经通关，无法进入该副本。
	public static final int TEAM_LEVEL_NO_OPEN=4113;//当前副本所属的阶段未开启，无法进入该副本。
	public static final int TEAM_LEVEL_TIPS=4114;//需要通过当前阶段，才可进入下一阶段。
	public static final int TEME_RBAC=4115;//只有小队队长才能进行该项操作。
	public static final int TEAM_FIGHT=4116;//副本中，无法进行该操作
	public static final int GROUP_NO_EXIT=4117;//团队不存在
	public static final int GROUP_FULL=4118;//队伍已满
	public static final int GROUP_LIMIT=4119;//没有该操作权限
	//排位赛
	public static final int LADDER_MAIL_TITLE=4120;//排位赛奖励
	public static final int LADDER_MAIL_CONTENT=4121;//亲爱的玩家您在上赛季段位[%s]特此奖励
	public static final int LADDER_NO_HONOR=4122;//荣誉点不够
	//公会副本
	public static final int GUILD_COPY_COUNT_NOT_ENOUGH=4123;//公会副本挑战次数不够
	public static final int GUILD_NOT_EXIST=4124;//公会不存在
	public static final int GUILD_COPY_HAS_OPEN=4125;//公会副本已经开启过了
	public static final int GUILD_COPY_DO_NOT_OPEN=4126;//公会副本还未开启
	public static final int GUILD_COPY_FIGHTING=4127;//公会副本有人挑战中
	public static final int GUILD_COPY_MAIL_TITLE=4128;//公会副本挑战阶段奖励
	public static final int GUILD_COPY_MAIL_CONTENT=4129;//公会副本挑战阶段奖励
	public static final int GUILD_LEVEL_LIMIT=4130;//公会等级不够
	public static final int GUILD_SKILL_UNLOCK=4131;//该科技未解锁
	public static final int GUILD_SKILL_MAX_LV=4132;//公会科技到最大等级了
	public static final int GUILD_DONT_OPEN=4133;//公会功能未开启
	//团队副本
	public static final int GROUP_LEADER_HAS_NOT_FIGHT=4160;//需要等待团长出战
	public static final int GROUP_ALREADY_START=4161;//副本已经开始，无法加入
	//宠物
	public static final int PET_SHOW_NOT_ENOUGH=4199;//两星宠物才可跟随玩家在主城显示
	public static final int PET_HAS_SAME_TYPE=4200;//拥有同类型宠物
	public static final int PET_MATERIAL_NOT_ENOUGH=4201;//宠物碎片不够
	public static final int PET_NOT_EXIST=4202;//宠物不存在
	public static final int PET_MATERIAL_NOT_EXIST=4203;//宠物碎片不存在
	public static final int PET_FIGHTING=4204;//宠物出战中
	//活动
	public static final int ACTIVITY_DONT_FINISH=4205;//活动未完成
	public static final int WEEKLY_CARD_TITLE=4206;//周卡邮件标题
	public static final int WEEKLY_CARD_CONTENT=4207;//周卡邮件内容
	public static final int MONTHLYLY_CARD_TITLE=4208;//月卡邮件标题
	public static final int MONTHLYLY_CARD_CONTENT=4209;//月卡邮件内容
	public static final int ACTIVATION_CODE_INVALID=4210;//激活码无效
	public static final int HAS_RECEIVE_GIFTBAG=4211;//已经领取过该礼包
	//称号
	public static final int TITLE_NOT_GET=4301;//未获得该称号
	public static final int TITLE_EQUIP=4302;//称号已装备
	//宠物玩法
	public static final int PET_PLAYING=4351;//宠物正在工作中
	public static final int PET_ACTIVITY_DOING=4352;//该活动正在进行中
	public static final int PET_ACTIVITY_NOT_OPEN=4353;//活动未开启
	public static final int PET_ACTIVITY_SAME=4354;//宠物重复
	//弹幕
	public static final int SCENE_NOT_EXIST=4360;//场景不存在
	public static final int BULLETSCREEN_NOT_SET=4361;//弹幕上限未设置
	public static final int LOCALBULLETSCREEN_ALL_ACCESS=4362;//本地弹幕已全部获取
	//任务提交
	public static final int OPERATION_TOO_FAST=4370;//您操作太频繁了
	//Facebook
	public static final int FACEBOOK_NO_PLAYER=4400;//玩家不存在
	public static final int FACEBOOK_INVITOR_NO_FACEBOOK=4401;//邀请者非Facebook账号登陆
	public static final int FACEBOOK_NO_FACEBOOK=4402;//非Facebook账号登陆
	public static final int FACEBOOK_NOT_INVITOR=4403;//非邀请玩家
	public static final int FACEBOOK_EMAIL_INVITE_TITLE=4410;//Facebook玩家邀请奖励
	//封禁提示
	public static final int BAN_LOGIN=4501;//账户处于封禁中，请联系客服
	public static final int BAN_IP=4502;//IP处于封禁中，请联系客服
	public static final int BAN_IMEI=4503;//设备处于封禁中，请联系客服
	public static final int BAN_CHAT=4504;//角色处于禁言中，请联系客服

}