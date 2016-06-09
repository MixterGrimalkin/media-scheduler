package net.amarantha.mediascheduler;

import com.google.inject.Inject;
import net.amarantha.mediascheduler.cue.Cue;
import net.amarantha.mediascheduler.exception.CueInUseException;
import net.amarantha.mediascheduler.exception.DuplicateCueException;
import net.amarantha.mediascheduler.exception.SchedulerException;
import net.amarantha.mediascheduler.http.HttpService;
import net.amarantha.mediascheduler.http.MockHttpService;
import net.amarantha.mediascheduler.midi.MidiCommand;
import net.amarantha.mediascheduler.midi.MidiService;
import net.amarantha.mediascheduler.midi.MockMidiService;
import net.amarantha.mediascheduler.scheduler.MediaEvent;
import net.amarantha.mediascheduler.scheduler.Schedule;
import net.amarantha.mediascheduler.scheduler.Scheduler;
import net.amarantha.mediascheduler.utility.Now;
import org.testng.Assert;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class TestCase {

    @Inject protected Now now;
    @Inject protected Scheduler scheduler;

    @Inject protected MidiService midi;
    @Inject protected HttpService http;

    protected void given_midi_device() {
        midi.openDevice();
    }

    protected void then_last_http_call_was_$1(String call) {
        if ( call==null ) {
            assertNull(((MockHttpService)http).getLastHttpCall());
        } else {
            assertEquals(call, ((MockHttpService) http).getLastHttpCall());
        }
    }

    protected void when_trigger_cue_$1(Cue cue) {
        cue.start();
    }

    protected void then_last_midi_command_was_$1(MidiCommand command) {
        if ( command==null ) {
            assertNull(((MockMidiService)midi).getLastMidiCommand());
        } else {
            Assert.assertEquals(command, ((MockMidiService) midi).getLastMidiCommand());
        }
    }

    //////////
    // When //
    //////////

    protected void when_start_scheduler() {
        scheduler.clearCues();
        scheduler.clearSchedules();
        scheduler.startup();
        scheduler.pause(true);      // We will manually "tick" the scheduler
    }


    protected void when_stop_scheduler() {
        scheduler.shutdown();
    }

    protected void when_date_is_$1(String date) {
        now.setDate(date);
        scheduler.checkSchedule();
    }

    protected void when_time_is_$1(String time) {
        now.setTime(time);
        scheduler.checkSchedule();
    }

    protected void when_add_cue_$1(Cue cue, boolean expectFail) {
        try {
            scheduler.addCue(cue);
            if ( expectFail ) {
                fail("Expected an exception");
            }
        } catch (Exception e) {
            if ( !expectFail ) {
                fail("Did not expect an exception: " + e.getMessage());
            }
            then_exception_thrown(DuplicateCueException.class, e.getClass());
        }
    }

    protected void when_remove_cue_$1(Cue cue, boolean expectFail) {
        try {
            scheduler.removeCue(cue);
            if ( expectFail ) {
                fail("Expected an exception");
            }
        } catch (Exception e) {
            if ( !expectFail ) {
                fail("Did not expect an exception: " + e.getMessage());
            }
            then_exception_thrown(CueInUseException.class, e.getClass());
        }
    }

    protected Integer when_add_priority_$1_event_$2_on_$3_from_$4_to_$5(int priority, Cue cue, String date, String start, String end, DayOfWeek... repeats) {
        return when_add_priority_$1_event_$2_on_$3_from_$4_to_$5(priority, cue, date, start, end, null, repeats);
    }

    protected Integer when_add_priority_$1_event_$2_on_$3_from_$4_to_$5(int priority, Cue cue, String date, String start, String end, Class<? extends Exception> expectedExceptionClass, DayOfWeek... repeats) {
        Integer result = null;
        try {
            result = priority==1
                    ? scheduler.addEvent(new MediaEvent(nextEventId++, cue.getId(), date, start, end, repeats)).getId()
                    : scheduler.addEvent(priority, new MediaEvent(nextEventId++, cue.getId(), date, start, end, repeats)).getId()
            ;
            if ( expectedExceptionClass!=null ) {
                fail("Expected an exception");
            }
        } catch (Exception e) {
            if (expectedExceptionClass == null) {
                fail("Did not expect an exception: " + e.getMessage());
            }
            then_exception_thrown(expectedExceptionClass, e.getClass());
        }
        return result;
    }

    protected void when_switch_event_$1_to_priority_$2(Integer eventId, int priority) {
        when_switch_event_$1_to_priority_$2(eventId, priority, null);
    }

    protected void when_switch_event_$1_to_priority_$2(Integer eventId, int priority, Class<? extends SchedulerException> expectedExceptionClass) {
        try {
            scheduler.switchPriority(eventId, priority);
            if ( expectedExceptionClass!=null ) {
                fail("Expected an exception");
            }
        } catch (SchedulerException e) {
            if (expectedExceptionClass == null) {
                fail("Did not expect an exception: " + e.getMessage());
            }
            then_exception_thrown(expectedExceptionClass, e.getClass());
        }
    }

    protected void when_remove_event_$1(long eventId) {
        scheduler.removeEvent(eventId);
    }

    private static int nextEventId = 1;


    //////////
    // Then //
    //////////

    protected void then_there_are_$1_cues(int count) {
        assertEquals(count, scheduler.getCues().size());
    }

    protected void then_there_are_$1_events_today(int count) {
        int total = 0;
        for (Map.Entry<Integer, Schedule> entry : scheduler.getSchedules().entrySet() ) {
            List<MediaEvent> events = entry.getValue().getEvents(now.date());
            total += events.size();
        }
        assertEquals(count, total);
    }

    protected void then_there_are_$1_events_between_$2_and_$3(int count, String fromStr, String toStr) {
        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to = LocalDate.parse(toStr);
        int total = 0;
        for (Map.Entry<Integer, Schedule> entry : scheduler.getSchedules().entrySet() ) {
            Map<LocalDate, List<MediaEvent>> events = entry.getValue().getEvents(from, to);
            for ( List<MediaEvent> list : events.values() ) {
                total += list.size();
            }
        }
        assertEquals(count, total);
    }

    protected void then_event_$1_exists_$2(Integer eventId, boolean exists) {
        MediaEvent event = scheduler.getEventById(eventId);
        assertEquals(exists, event!=null);
        try {
            event = scheduler.switchPriority(eventId, Scheduler.MAX_PRIORITY);
            assertEquals(exists, event!=null);
        } catch (SchedulerException e) {
            fail("Did not expect an exception: " + e.getMessage());
        }
    }

    protected void then_event_$1_is_$2(Integer eventId, Cue cue) {
        MediaEvent actualEvent = scheduler.getEventById(eventId);
        assertEquals(cue.getId(), actualEvent.getCueId());
    }

    protected void then_event_$1_end_time_id_$2(long eventId, String endTime) {
        MediaEvent actualEvent = scheduler.getEventById(eventId);
        assertEquals(endTime, actualEvent.getEndTimeString());
    }

    protected void then_current_cue_is_$1(Cue cue) {
        MediaEvent currentEvent = scheduler.getCurrentEvent();
        if ( currentEvent==null ) {
            assertNull(cue);
        } else {
            assertEquals(cue.getId(), currentEvent.getCueId());
        }
    }

    protected void then_exception_thrown(Class<? extends Exception> expectedExceptionClass, Class<? extends Exception> actualExceptionClass) {
        if (actualExceptionClass != expectedExceptionClass) {
            fail("Wrong exception thrown");
        }
    }



}
