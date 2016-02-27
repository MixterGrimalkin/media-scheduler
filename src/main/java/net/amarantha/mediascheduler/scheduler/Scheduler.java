package net.amarantha.mediascheduler.scheduler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.amarantha.mediascheduler.device.ArKaos;
import net.amarantha.mediascheduler.device.Projector;
import net.amarantha.mediascheduler.entity.CueList;
import net.amarantha.mediascheduler.entity.MediaEvent;
import net.amarantha.mediascheduler.exception.CueListInUseException;
import net.amarantha.mediascheduler.exception.CueListNotFoundException;
import net.amarantha.mediascheduler.exception.PriorityOutOfBoundsException;
import net.amarantha.mediascheduler.exception.ScheduleConflictException;
import net.amarantha.mediascheduler.utility.Now;

import java.util.*;
import java.util.Map.Entry;

@Singleton
public class Scheduler {

    @Inject private ArKaos mediaServer;
    @Inject private Projector projector;

    @Inject private Now now;

    public Scheduler() {}

    ///////////////
    // Cue Lists //
    ///////////////

    private Set<CueList> cueLists = new HashSet<>();

    public CueList addCueList(CueList cueList) {
        cueLists.add(cueList);
        return cueList;
    }

    public Set<CueList> getCueLists() {
        return cueLists;
    }

    public void removeCueList(CueList cueList) throws CueListInUseException {
        List<MediaEvent> events = getEventsByCueList(cueList);
        if ( events.isEmpty() ) {
            cueLists.remove(cueList);
        } else {
            throw new CueListInUseException("CueList " + cueList + " is used by " + events.size() + " events");
        }
    }

    void clearCueLists() {
        cueLists.clear();
    }


    ///////////////
    // Schedules //
    ///////////////

    private Map<Integer, Schedule> schedules = new LinkedHashMap<>();

    public static final int MAX_PRIORITY = 10;

    void clearSchedules() {
        schedules.clear();
    }

    public Schedule createSchedule(int priority) throws PriorityOutOfBoundsException {
        if ( priority < 1 || priority > MAX_PRIORITY ) {
            throw new PriorityOutOfBoundsException("Priority must be between 1 (lowest) and " + MAX_PRIORITY + " (highest)");
        }
        Schedule schedule = new Schedule();
        schedules.put(priority, schedule);
        return schedule;
    }

    public Map<Integer, Schedule> getSchedules() {
        return schedules;
    }

    public MediaEvent getCurrentEvent() {
        for ( int priority = MAX_PRIORITY; priority>0; priority-- ) {
            Schedule schedule = schedules.get(priority);
            if ( schedule!=null ) {
                MediaEvent event = schedule.getEvent(now.now());
                if ( event!=null ) {
                    return event;
                }
            }
        }
        return null;
    }

    public MediaEvent addEvent(MediaEvent event) throws ScheduleConflictException, CueListNotFoundException {
        try {
            return addEvent(1, event);
        } catch (PriorityOutOfBoundsException ignored) {}
        return null;
    }

    public MediaEvent addEvent(int priority, MediaEvent event) throws PriorityOutOfBoundsException, ScheduleConflictException, CueListNotFoundException {
        CueList cueList = event.getCueList();
        if ( !cueLists.contains(cueList) ) {
            throw new CueListNotFoundException("Cue List " + (event==null?"":cueList.getNumber()+":"+cueList.getName()) + " not found");
        }
        Schedule schedule = schedules.get(priority);
        if ( schedule==null ) {
            schedule = createSchedule(priority);
        }
        schedule.addEvent(event);
        checkSchedule();
        return event;
    }

    public boolean removeEvent(long eventId) {
        boolean removed = false;
        for ( Entry<Integer, Schedule> entry : schedules.entrySet() ) {
            removed |= entry.getValue().removeEvent(eventId);
        }
        checkSchedule();
        return removed;
    }

    public MediaEvent getEventById(long eventId) {
        for ( Entry<Integer, Schedule> entry : schedules.entrySet() ) {
            MediaEvent event = entry.getValue().getEventById(eventId);
            if ( event!=null ) {
                return event;
            }
        }
        return null;
    }

    public List<MediaEvent> getEventsByCueList(CueList cueList) {
        List<MediaEvent> result = new ArrayList<>();
        for ( Entry<Integer, Schedule> entry : schedules.entrySet() ) {
            Schedule schedule = entry.getValue();
            result.addAll(schedule.getEventsByCueList(cueList));
        }
        return result;
    }

    public MediaEvent switchPriority(long eventId, int priority) throws PriorityOutOfBoundsException, ScheduleConflictException, CueListNotFoundException {
        for ( Entry<Integer, Schedule> entry : schedules.entrySet() ) {
            Schedule oldSchedule = entry.getValue();
            MediaEvent event = oldSchedule.getEventById(eventId);
            if ( event!=null ) {
                addEvent(priority, event);
                oldSchedule.removeEvent(event.getId());
                checkSchedule();
                return event;
            }
        }
        return null;
    }


    ////////////////////////
    // Startup & Shutdown //
    ////////////////////////

    private Timer timer;
    private boolean paused = false;

    public void startup() {
        mediaServer.startup();
        projector.switchOn(true);
        startSchedulerLoop();
    }

    private void startSchedulerLoop() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if ( !paused ) {
                    checkSchedule();
                }
            }
        }, 0, 1000);
    }

    public void pause(boolean paused) {
        this.paused = paused;
    }

    void checkSchedule() {
        MediaEvent currentEvent = getCurrentEvent();
        CueList nextCueList = ( currentEvent==null ? null : currentEvent.getCueList() );
        CueList currentCueList = mediaServer.getCurrentCueList();
        if ( nextCueList==null ) {
            if ( currentCueList!=null ) {
                mediaServer.stopCueList();
            }
        } else {
            if ( !nextCueList.equals(currentCueList) ) {
                mediaServer.startCueList(currentEvent.getCueList());
            }
        }
    }

    public void shutdown() {
        mediaServer.shutdown();
        projector.switchOn(false);
        if ( timer!=null ) {
            timer.cancel();
        }
    }

}
