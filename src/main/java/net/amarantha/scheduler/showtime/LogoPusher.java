package net.amarantha.scheduler.showtime;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.amarantha.scheduler.http.HostManager;
import net.amarantha.scheduler.http.HttpService;

import java.util.Timer;
import java.util.TimerTask;

@Singleton
public class LogoPusher {

    @Inject private HttpService http;
    @Inject private HostManager hosts;

    private Timer timer = new Timer();

    public void start() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                fire();
            }
        }, 7000, 60000);
        System.out.println("LogoPusher Active");
    }

    public void stop() {
        timer.cancel();
    }

    private boolean logo = true;

    private void fire() {
        if (logo) {
            System.out.println("Logo -->");
            fireScene("greenpeace-logo");
        } else {
            System.out.println("Flash Message -->");
            fireScene("single-message");
        }
        logo = !logo;
    }

    private void fireScene(String scene) {
        for ( String host : hosts.getHosts("logo") ) {
            System.out.println("--> "+host);
            http.postAsync(null, host, "lightboard/scene/"+scene+"/load", "");
        }
    }

}