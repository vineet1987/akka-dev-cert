package io.example.application;

import akka.javasdk.testkit.TestKitSupport;
import io.example.domain.Participant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.*;

public class ParticipantSlotsViewTest extends TestKitSupport {

    static Participant studentParticipant = new Participant("alice", Participant.ParticipantType.STUDENT);
    static Participant aircraftParticipant = new Participant("superplane", Participant.ParticipantType.AIRCRAFT);
    static Participant instructorParticipant = new Participant("superteacher", Participant.ParticipantType.INSTRUCTOR);

    static List<Participant> participantList = List.of(studentParticipant, aircraftParticipant, instructorParticipant);

    @Test
    public void testQueryParticipantAvailabilityTest() {
        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = componentClient.forEventSourcedEntity("bestslot")
                    .method(BookingSlotEntity::markSlotAvailable)
                    .invoke(command);

            assertEquals(done(), result);
            sleep(5);

            var slotsList = querySlotsByParticipantIdAndStatus(p.id(), "available").slots();
            assertEquals(1, slotsList.size());

            var slotRow = slotsList.getFirst();
            assertEquals("bestslot", slotRow.slotId());
            assertEquals(p.id(), slotRow.participantId());
            assertEquals(p.participantType().name(), slotRow.participantType());
            assertTrue(slotRow.bookingId().isEmpty());
        });
    }

    @Test
    public void testQueryParticipantBookedTest() {
        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = componentClient.forEventSourcedEntity("bestslot1")
                    .method(BookingSlotEntity::markSlotAvailable)
                    .invoke(command);

            assertEquals(done(), result);
        });

        var bookCommand = new BookingSlotEntity.Command.BookReservation(studentParticipant.id(),
                aircraftParticipant.id(), instructorParticipant.id(), "booking1");
        var bookResult = componentClient.forEventSourcedEntity("bestslot1")
                .method(BookingSlotEntity::bookSlot)
                .invoke(bookCommand);

        assertEquals(done(), bookResult);

        sleep(5);

        participantList.forEach(p -> {
            var slotsList = querySlotsByParticipantIdAndStatus(p.id(), "booked").slots();
            assertEquals(1, slotsList.size());

            var slotRow = slotsList.getFirst();
            assertEquals("bestslot1", slotRow.slotId());
            assertEquals(p.id(), slotRow.participantId());
            assertEquals(p.participantType().name(), slotRow.participantType());
            assertEquals("booking1", slotRow.bookingId());
        });
    }

    @Test
    public void testQueryParticipantCancelBookingTest() {
        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = componentClient.forEventSourcedEntity("bestslot2")
                    .method(BookingSlotEntity::markSlotAvailable)
                    .invoke(command);

            assertEquals(done(), result);
        });

        var bookCommand = new BookingSlotEntity.Command.BookReservation(studentParticipant.id(),
                aircraftParticipant.id(), instructorParticipant.id(), "booking2");
        var bookResult = componentClient.forEventSourcedEntity("bestslot2")
                .method(BookingSlotEntity::bookSlot)
                .invoke(bookCommand);

        assertEquals(done(), bookResult);

        var cancelBookingResult = componentClient.forEventSourcedEntity("bestslot2")
                .method(BookingSlotEntity::cancelBooking)
                .invoke("booking2");

        assertEquals(done(), cancelBookingResult);

        sleep(5);

        participantList.forEach(p -> {
            var slotsList = querySlotsByParticipantIdAndStatus(p.id(), "canceled").slots().stream().filter(slot -> slot.slotId().equals("bestslot2")).toList();
            assertEquals(1, slotsList.size());

            var slotRow = slotsList.getFirst();
            assertEquals(p.id(), slotRow.participantId());
            assertEquals(p.participantType().name(), slotRow.participantType());
        });
    }

    private ParticipantSlotsView.SlotList querySlotsByParticipantIdAndStatus(String participantId, String status) {
        return await(
                componentClient.forView()
                        .method(ParticipantSlotsView::getSlotsByParticipantAndStatus)
                        .invokeAsync(new ParticipantSlotsView.ParticipantStatusInput(participantId, status)));
    }

    private ParticipantSlotsView.SlotList querySlotsByParticipant(String participantId) {
        return await(
                componentClient.forView()
                        .method(ParticipantSlotsView::getSlotsByParticipant)
                        .invokeAsync(participantId));
    }

    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
