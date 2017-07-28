package com.game.params;

import java.util.List;
import java.util.ArrayList;

//每日数据(工具自动生成，请勿手动修改！）
public class DailyVo implements IProtocol {
	public long monthCardEnd;//月卡到期时间
	public boolean monthCard;//是否月卡
	public List<Integer> vipBag;//vip礼包领取记录
	public List<Integer> charges;//特殊类型的充值记录
	public boolean todayVipReward;//是否领取当日vip福利
	public boolean todayMonthCard;//月卡福利
	public int fundOpen;//基金开启
	public List<Integer> fundsTake;//已经领取的基金等级
	public short loginDays;//登录天数
	public List<Int2Param> dailys;//每日次数相关，前端要找个地方定义id
	public int signFlag;//签到标识


	public void decode(BufferBuilder bb) {
		this.monthCardEnd = bb.getLong();
		this.monthCard = bb.getBoolean();
		this.vipBag = bb.getIntList();
		this.charges = bb.getIntList();
		this.todayVipReward = bb.getBoolean();
		this.todayMonthCard = bb.getBoolean();
		this.fundOpen = bb.getInt();
		this.fundsTake = bb.getIntList();
		this.loginDays = bb.getShort();
		
        if (bb.getNullFlag())
            this.dailys = null;
        else {
            int length = bb.getInt();
            this.dailys = new ArrayList<Int2Param>();
            for (int i = 0; i < length; i++)
            {
                //如果元素不够先创建一个，Java泛型创建对象，性能？
                boolean isNull = bb.getNullFlag();

                //如果不是null就解析
                if(isNull)
                {
                    this.dailys.add(null);
                }
                else
                {
                    Int2Param instance = new Int2Param();
                    instance.decode(bb);
                    this.dailys.add(instance);
                }

            }
        }
		this.signFlag = bb.getInt();
	}

	public void encode(BufferBuilder bb) {
		bb.putLong(this.monthCardEnd);
		bb.putBoolean(this.monthCard);
		bb.putIntList(this.vipBag);
		bb.putIntList(this.charges);
		bb.putBoolean(this.todayVipReward);
		bb.putBoolean(this.todayMonthCard);
		bb.putInt(this.fundOpen);
		bb.putIntList(this.fundsTake);
		bb.putShort(this.loginDays);
		bb.putProtocolVoList(this.dailys);
		bb.putInt(this.signFlag);
	}
}
