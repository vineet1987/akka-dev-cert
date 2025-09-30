package io.example.application;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class BookingSlotEntityTest {

    static Participant studentParticipant = new Participant("alice", Participant.ParticipantType.STUDENT);
    static Participant aircraftParticipant = new Participant("superplane", Participant.ParticipantType.AIRCRAFT);
    static Participant instructorParticipant = new Participant("superteacher", Participant.ParticipantType.INSTRUCTOR);

    static List<Participant> participantList = List.of(studentParticipant, aircraftParticipant, instructorParticipant);

    @Test
    void testMarkSlotAvailable() {
        var testKit = EventSourcedTestKit.of("bestbooking", BookingSlotEntity::new);
        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
            assertTrue(result.isReply());

            var event = result.getNextEventOfType(BookingEvent.ParticipantMarkedAvailable.class);
            assertEquals("bestbooking", event.slotId());
            assertEquals(p.id(), event.participantId());
            assertEquals(p.participantType(), event.participantType());
        });
    }

    @Test
    void testUnmarkSlotAvailable() {
        var testKit = EventSourcedTestKit.of("bestbooking", BookingSlotEntity::new);
        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
            assertTrue(result.isReply());

            var unmarkSlotAvailableCommand = new BookingSlotEntity.Command.UnmarkSlotAvailable(p);
            var unmarkSlotAvailableResult = testKit.method(BookingSlotEntity::unmarkSlotAvailable).invoke(unmarkSlotAvailableCommand);
            assertTrue(unmarkSlotAvailableResult.isReply());

            var event = unmarkSlotAvailableResult.getNextEventOfType(BookingEvent.ParticipantUnmarkedAvailable.class);
            assertEquals("bestbooking", event.slotId());
            assertEquals(p.id(), event.participantId());
            assertEquals(p.participantType(), event.participantType());
        });
    }

    @Test
    void testFailUnmarkSlotAvailable() {
        var testKit = EventSourcedTestKit.of("bestbooking", BookingSlotEntity::new);
        participantList.forEach(p -> {
            var unmarkSlotAvailableCommand = new BookingSlotEntity.Command.UnmarkSlotAvailable(p);
            var unmarkSlotAvailableResult = testKit.method(BookingSlotEntity::unmarkSlotAvailable).invoke(unmarkSlotAvailableCommand);
            assertFalse(unmarkSlotAvailableResult.isReply());

            assertEquals(String.format("Participant with Id: %1$s and type: %2$s is not available",
                    p.id(), p.participantType()), unmarkSlotAvailableResult.getError());

            assertTrue(unmarkSlotAvailableResult.getAllEvents().isEmpty());
        });
    }

    @Test
    void testBookReservation() {
        var testKit = EventSourcedTestKit.of("bestSlot", BookingSlotEntity::new);
        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
            assertTrue(result.isReply());
        });

        var bookReservationCommand = new BookingSlotEntity.Command.BookReservation(
                studentParticipant.id(),
                aircraftParticipant.id(),
                instructorParticipant.id(),
                "bestbooking"
        );

        var bookReservationResult = testKit.method(BookingSlotEntity::bookSlot).invoke(bookReservationCommand);
        assertTrue(bookReservationResult.isReply());

        var participantBookedEvents = bookReservationResult.getAllEvents();
        assertEquals(3, participantBookedEvents.size());

        participantBookedEvents.forEach(event -> {
            assertEquals(BookingEvent.ParticipantBooked.class, event.getClass());
            var participantBookedEvent = (BookingEvent.ParticipantBooked) event;
            assertEquals("bestbooking", participantBookedEvent.bookingId());
            assertEquals("bestSlot", participantBookedEvent.slotId());
        });

        var state = testKit.getState();
        var bookings = state.bookings();
        var available = state.available();

        assertEquals(3, bookings.size());
        assertEquals(0, available.size());

        var bookingIds = bookings.stream().map(Timeslot.Booking::bookingId).toList().stream().distinct().toList();
        assertEquals(1, bookingIds.size());
        assertEquals("bestbooking", bookingIds.getFirst());
    }

    @Test
    void testFailBookReservationWhenNoAvailableParticipants() {
        var testKit = EventSourcedTestKit.of("bestSlot", BookingSlotEntity::new);

        var bookReservationCommand = new BookingSlotEntity.Command.BookReservation(
                studentParticipant.id(),
                aircraftParticipant.id(),
                instructorParticipant.id(),
                "bestbooking"
        );

        var bookReservationResult = testKit.method(BookingSlotEntity::bookSlot).invoke(bookReservationCommand);
        assertFalse(bookReservationResult.isReply());

        var state = testKit.getState();
        var bookings = state.bookings();
        var available = state.available();

        assertEquals(0, bookings.size());
        assertEquals(0, available.size());
    }

    @Test
    void testFailBookReservationWhenInstructorIsNotAvailable() {
        var testKit = EventSourcedTestKit.of("bestSlot", BookingSlotEntity::new);

        List.of(studentParticipant, aircraftParticipant).forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
            assertTrue(result.isReply());
        });

        var bookReservationCommand = new BookingSlotEntity.Command.BookReservation(
                studentParticipant.id(),
                aircraftParticipant.id(),
                instructorParticipant.id(),
                "bestbooking"
        );

        var bookReservationResult = testKit.method(BookingSlotEntity::bookSlot).invoke(bookReservationCommand);
        assertFalse(bookReservationResult.isReply());

        var state = testKit.getState();
        var bookings = state.bookings();
        var available = state.available();

        assertEquals(0, bookings.size());
        assertEquals(2, available.size());
    }
}
