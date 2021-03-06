package com.game.module.attach;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.game.module.attach.catchgold.CatchGoldAttach;
import com.game.module.attach.catchgold.CatchGoldLogic;
import com.game.module.attach.leadaway.LeadAwayLogic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.game.event.InitHandler;
import com.game.module.attach.arena.ArenaLogic;
import com.game.module.attach.charge.ChargeActivityLogic;
import com.game.module.attach.endless.EndlessLogic;
import com.game.module.attach.experience.ExperienceLogic;
import com.game.module.attach.lottery.LotteryLogic;
import com.game.module.attach.training.trainingLogic;
import com.game.module.attach.treasure.TreasureLogic;
import com.game.util.BeanManager;
import com.server.util.ServerLogger;

@Service
public class AttachService implements InitHandler {

	@Autowired
	private AttachDao dao;
	
	
	private final Map<Integer, Map<Byte, Attach>> attachMapping = new ConcurrentHashMap<Integer, Map<Byte, Attach>>();
	private final Map<Byte, AttachLogic<?>> logicMapping = new ConcurrentHashMap<Byte, AttachLogic<?>>();

	@Override
	public void handleInit() {
		//累计充值
		register(BeanManager.getBean(ChargeActivityLogic.class));
		//无尽漩涡
		register(BeanManager.getBean(EndlessLogic.class));
		//金币副本
		register(BeanManager.getBean(TreasureLogic.class));
		//顺手牵羊副本
		register(BeanManager.getBean(LeadAwayLogic.class));
		//金币副本副本
		register(BeanManager.getBean(CatchGoldLogic.class));
		//经验副本
		register(BeanManager.getBean(ExperienceLogic.class));
		//AI竞技场
		register(BeanManager.getBean(ArenaLogic.class));
		//英雄试练
		register(BeanManager.getBean(trainingLogic.class));
		//抽奖
		register(BeanManager.getBean(LotteryLogic.class));
	}
	
	private void register(AttachLogic<?> logic){
		if(logicMapping.putIfAbsent(logic.getType(), logic) != null){
			throw new DuplicateKeyException("can't register the same logic,type=" + logic.getType());
		}
		logic.handleInit();
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Attach> T getAttach(int playerId, byte type){
		
		Map<Byte, Attach> attachs = attachMapping.get(playerId);
		if(attachs == null){
			attachs = new ConcurrentHashMap<Byte, Attach>();
			attachMapping.put(playerId, attachs);
			List<Attach> list = dao.getAttach(playerId);
			if(list != null && !list.isEmpty()){
				for(Attach attach : list){
					AttachLogic<?> logic = logicMapping.get(attach.getType());
					if(logic == null) continue;
					attachs.put(attach.getType(), attach.wrap(logic.getAttachClass()));
				}
			}
		}
		Attach attach = attachs.get(type);
		AttachLogic<?> logic = logicMapping.get(type);
		if(attach == null){
			if(logic == null){
				ServerLogger.warn("can't find the logic for the attach, type=" + type);
				return null;
			}
			attach = logic.generalNewAttach(playerId);
			if(attach != null){
				//查询的时候不插入了
				//dao.insert(attach);
				attach.setInsert(true);
				attachs.put(type, attach);
			}
		}
		return (T)attach;
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Attach> List<T> getAttachaByType(byte type){
		AttachLogic<?> logic = logicMapping.get(type);
		List<T> result = new ArrayList<T>();
		List<Attach> list = dao.getAllAttachByType(type);
		if(list != null && !list.isEmpty()){
			for(Attach attach : list){
				result.add((T)attach.wrap(logic.getAttachClass()));
			}
		}
		return result;
	}
	
	public List<Integer> getAllPlayer(byte type){
		return dao.getAllPlayer(type);
	}
	
	public void clear(byte type){
		synchronized (attachMapping) {
			for(Map.Entry<Integer, Map<Byte, Attach>> entry : attachMapping.entrySet()){
				entry.getValue().remove(type);
			}
			dao.clear(type);
		}
	}

}
