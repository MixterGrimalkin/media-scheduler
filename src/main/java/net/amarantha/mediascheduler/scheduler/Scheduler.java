package net.amarantha.mediascheduler.scheduler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.amarantha.mediascheduler.device.ArKaos;
import net.amarantha.mediascheduler.device.Projector;
import net.amarantha.mediascheduler.exception.*;
import net.amarantha.mediascheduler.utility.Now;

import java.util.*;
import java.util.Map.Entry;

@Singleton
public class Scheduler {

    @Inject private ArKaos mediaServer;
    @Inject private Projector projector;
    @Inject private JsonEncoder json;

    @Inject private Now now;

    public Scheduler() {}

    //////////
    // Cues //
    //////////

    private Set<Cue> cues = new HashSet<>();

    private static final String CUES_FILE = "cues.json";

    private void loadCues() {
        cues = json.decodeCuesFromFile(CUES_FILE);
        for ( Cue cue : cues ) {
            nextCueId = Math.max(cue.getId()+1, nextCueId);
        }
    }

    public void saveCues() {
        json.encodeCuesToFile(CUES_FILE);
    }

    public Set<Cue> getCues() {
        return cues;
    }

    public Cue getCue(int id) {
        for ( Cue cue : cues) {
            if ( cue.getId()==id ) {
                return cue;
            }
        }
        return null;
    }

    public int addCue(Integer number, String name) {
        try {
            return addCue(new Cue(nextCueId++, number, name));
        } catch (DuplicateCueException ignored) {}
        return -1;
    }

    public int addCue(Cue cue) throws DuplicateCueException {
        if ( getCue(cue.getId())!=null ) {
            throw new DuplicateCueException();
        }
        cues.add(cue);
        saveCues();
        return cue.getId();
    }

    public void removeCue(int id) throws CueInUseException {
        removeCue(getCue(id));
    }

    public void removeCue(Cue cue) throws CueInUseException {
        List<MediaEvent> events = getEventsByCue(cue);
        if ( events.isEmpty() ) {
            cues.remove(cue);
        } else {
            throw new CueInUseException("Cue " + cue + " is used by " + events.size() + " events");
        }
        saveCues();
    }

    void clearCues() {
        cues.clear();
        saveCues();
    }

    public static int nextCueId = 1;
    public static int nextEventId = 1;


    ///////////////
    // Schedules //
    ///////////////

    public void loadSchedules() {

    }

    public void saveSchedules() {

    }

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

    public MediaEvent addEvent(MediaEvent event) throws ScheduleConflictException, CueNotFoundException {
        try {
            return addEvent(1, event);
        } catch (PriorityOutOfBoundsException ignored) {}
        return null;
    }

    public MediaEvent addEvent(int priority, MediaEvent event) throws PriorityOutOfBoundsException, ScheduleConflictException, CueNotFoundException {
        Cue cue = getCue(event.getCueId());
        if ( cue ==null ) {
            throw new CueNotFoundException("Cue List " + event.getCueId() + " not found");
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

    public List<MediaEvent> getEventsByCue(Cue cue) {
        List<MediaEvent> result = new ArrayList<>();
        for ( Entry<Integer, Schedule> entry : schedules.entrySet() ) {
            Schedule schedule = entry.getValue();
            result.addAll(schedule.getEventsByCueList(cue));
        }
        return result;
    }

    public MediaEvent switchPriority(long eventId, int priority) throws PriorityOutOfBoundsException, ScheduleConflictException, CueNotFoundException {
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
        loadCues();
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
        Cue nextCue = ( currentEvent==null ? null : getCue(currentEvent.getCueId()) );
        Cue currentCue = mediaServer.getCurrentCue();
        if ( nextCue ==null ) {
            if ( currentCue !=null ) {
                mediaServer.stopAll();
            }
        } else {
            if ( !nextCue.equals(currentCue) ) {
                mediaServer.startCueList(getCue(currentEvent.getCueId()));
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
