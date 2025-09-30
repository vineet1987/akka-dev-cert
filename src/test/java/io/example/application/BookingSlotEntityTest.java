package io.example.application;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

class BookingSlotEntityTest {

    static Participant studentParticipant = new Participant("alice", Participant.ParticipantType.STUDENT);
    static Participant aircraftParticipant = new Participant("superplane", Participant.ParticipantType.AIRCRAFT);
    static Participant instructorParticipant = new Participant("superteacher", Participant.ParticipantType.INSTRUCTOR);

    static List<Participant> participantList = List.of(studentParticipant, aircraftParticipant, instructorParticipant);

    @Test
    void testZeroBookingsAndZeroAvailable() {
        var slot = generateSlot();
        var testKit = EventSourcedTestKit.of(slot, BookingSlotEntity::new);
        participantList.forEach(p -> {
            var result = testKit.method(BookingSlotEntity::getSlot).invoke();
            assertTrue(result.isReply());

            var timeSlot = (Timeslot) result.getUpdatedState();
            assertTrue(timeSlot.bookings().isEmpty());
            assertTrue(timeSlot.available().isEmpty());
        });
    }

    @Test
    void testMarkSlotAvailable() {
        var slot = generateSlot();
        var testKit = EventSourcedTestKit.of(slot, BookingSlotEntity::new);
        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
            assertTrue(result.isReply());

            var event = result.getNextEventOfType(BookingEvent.ParticipantMarkedAvailable.class);
            assertEquals(slot, event.slotId());
            assertEquals(p.id(), event.participantId());
            assertEquals(p.participantType(), event.participantType());
        });
    }

    @Test
    void testUnmarkSlotAvailable() {
        var slot = generateSlot();
        var testKit = EventSourcedTestKit.of(slot, BookingSlotEntity::new);
        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
            assertTrue(result.isReply());

            var unmarkSlotAvailableCommand = new BookingSlotEntity.Command.UnmarkSlotAvailable(p);
            var unmarkSlotAvailableResult = testKit.method(BookingSlotEntity::unmarkSlotAvailable).invoke(unmarkSlotAvailableCommand);
            assertTrue(unmarkSlotAvailableResult.isReply());

            var event = unmarkSlotAvailableResult.getNextEventOfType(BookingEvent.ParticipantUnmarkedAvailable.class);
            assertEquals(slot, event.slotId());
            assertEquals(p.id(), event.participantId());
            assertEquals(p.participantType(), event.participantType());
        });
    }

    @Test
    void testFailUnmarkSlotAvailable() {
        var slot = generateSlot();
        var testKit = EventSourcedTestKit.of(slot, BookingSlotEntity::new);
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
        var slot = generateSlot();
        var testKit = EventSourcedTestKit.of(slot, BookingSlotEntity::new);
        var bookingId = generateBookingId();

        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
            assertTrue(result.isReply());
        });

        var bookReservationCommand = new BookingSlotEntity.Command.BookReservation(
                studentParticipant.id(),
                aircraftParticipant.id(),
                instructorParticipant.id(),
                bookingId
        );

        var bookReservationResult = testKit.method(BookingSlotEntity::bookSlot).invoke(bookReservationCommand);
        assertTrue(bookReservationResult.isReply());

        var participantBookedEvents = bookReservationResult.getAllEvents();
        assertEquals(3, participantBookedEvents.size());

        participantBookedEvents.forEach(event -> {
            assertEquals(BookingEvent.ParticipantBooked.class, event.getClass());
            var participantBookedEvent = (BookingEvent.ParticipantBooked) event;
            assertEquals(bookingId, participantBookedEvent.bookingId());
            assertEquals(slot, participantBookedEvent.slotId());
        });

        var state = testKit.getState();
        var bookings = state.bookings();
        var available = state.available();

        assertEquals(3, bookings.size());
        assertEquals(0, available.size());

        var bookingIds = bookings.stream().map(Timeslot.Booking::bookingId).toList().stream().distinct().toList();
        assertEquals(1, bookingIds.size());
        assertEquals(bookingId, bookingIds.getFirst());
    }

    @Test
    void testReBookingABookedSlotShouldFail() {
        var slot = generateSlot();
        var testKit = EventSourcedTestKit.of(slot, BookingSlotEntity::new);

        var bookingId = generateBookingId();
        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
            assertTrue(result.isReply());
        });

        var bookReservationCommand = new BookingSlotEntity.Command.BookReservation(
                studentParticipant.id(),
                aircraftParticipant.id(),
                instructorParticipant.id(),
                bookingId
        );

        var bookReservationResult = testKit.method(BookingSlotEntity::bookSlot).invoke(bookReservationCommand);
        assertTrue(bookReservationResult.isReply());

        var reBookReservationResult = testKit.method(BookingSlotEntity::bookSlot).invoke(bookReservationCommand);
        assertFalse(reBookReservationResult.isReply());
    }

    @Test
    void testFailBookReservationWhenNoAvailableParticipants() {
        var slot = generateSlot();
        var testKit = EventSourcedTestKit.of(slot, BookingSlotEntity::new);

        var bookingId = generateBookingId();

        var bookReservationCommand = new BookingSlotEntity.Command.BookReservation(
                studentParticipant.id(),
                aircraftParticipant.id(),
                instructorParticipant.id(),
                bookingId
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
        var slot = generateSlot();
        var testKit = EventSourcedTestKit.of(slot, BookingSlotEntity::new);

        var bookingId = generateBookingId();

        List.of(studentParticipant, aircraftParticipant).forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = testKit.method(BookingSlotEntity::markSlotAvailable).invoke(command);
            assertTrue(result.isReply());
        });

        var bookReservationCommand = new BookingSlotEntity.Command.BookReservation(
                studentParticipant.id(),
                aircraftParticipant.id(),
                instructorParticipant.id(),
                bookingId
        );

        var bookReservationResult = testKit.method(BookingSlotEntity::bookSlot).invoke(bookReservationCommand);
        assertFalse(bookReservationResult.isReply());

        var state = testKit.getState();
        var bookings = state.bookings();
        var available = state.available();

        assertEquals(0, bookings.size());
        assertEquals(2, available.size());
    }

    private String generateSlot() {
        return "bestSlot-" + UUID.randomUUID();
    }

    private String generateBookingId() {
        return "booking-" + UUID.randomUUID();
    }
}
