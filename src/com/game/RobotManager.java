package com.game;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.log4j.xml.DOMConfigurator;

import java.util.List;
import java.util.Map;

/**
 * 模拟发包工具
 */
public class RobotManager {

    private static Map<String, List<Roboter>> robotMap = Maps.newConcurrentMap();

    public static void main(String[] args) throws Exception {
        SysConfig.init();
        DOMConfigurator.configure("config/log4j.xml");
        final String host = "111.230.210.206";
        //final String host = "192.168.6.223";
        final int port = 10010;

        for (int i = 0; i < 30; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String pfx = Thread.currentThread().getName();
                    List<Roboter> list = Lists.newArrayList();
                    try {
                        for (int i = 0; i < 64; i++) {
                            Roboter roboter = new Roboter(pfx + "_AN1Q_" + i, host, port);
                            list.add(roboter);
                            Thread.sleep(200);
                        }
                    } catch (Exception e) {

                    }

                    while (true) {
                        boolean flag = false;
                        for (Roboter roboter : list) {
                            flag |= roboter.sendRobotMsg();
                        }
                        if (!flag) {
                            break;
                        }
                    }
                }
            }).start();
        }
    }
}
