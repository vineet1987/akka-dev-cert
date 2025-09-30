package io.example.application;

import akka.javasdk.testkit.TestKitSupport;
import io.example.domain.Participant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantSlowsViewTest extends TestKitSupport {

    static Participant studentParticipant = new Participant("alice", Participant.ParticipantType.STUDENT);
    static Participant aircraftParticipant = new Participant("superplane", Participant.ParticipantType.AIRCRAFT);
    static Participant instructorParticipant = new Participant("superteacher", Participant.ParticipantType.INSTRUCTOR);

    static List<Participant> participantList = List.of(studentParticipant, aircraftParticipant, instructorParticipant);

    @Test
    public void testQueryParticipantAvailabilityTest() {
        var slot = generateSlot();

        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = componentClient.forEventSourcedEntity(slot)
                    .method(BookingSlotEntity::markSlotAvailable)
                    .invoke(command);
            assertEquals(done(), result);
        });

        sleep(5);

        participantList.forEach(p -> {
            var slotsList = querySlotsByParticipantIdAndStatus(p.id(), "available").slots();
            assertEquals(1, slotsList.size());

            var slotRow = slotsList.getFirst();
            assertEquals(slot, slotRow.slotId());
            assertEquals(p.id(), slotRow.participantId());
            assertEquals(p.participantType().name(), slotRow.participantType());
            assertTrue(slotRow.bookingId().isEmpty());
        });
    }

    @Test
    public void testQueryParticipantBookedTest() {
        var slot = generateSlot();
        var bookingId = generateBooking();

        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = componentClient.forEventSourcedEntity(slot)
                    .method(BookingSlotEntity::markSlotAvailable)
                    .invoke(command);

            assertEquals(done(), result);
        });

        var bookCommand = new BookingSlotEntity.Command.BookReservation(studentParticipant.id(),
                aircraftParticipant.id(), instructorParticipant.id(), bookingId);
        var bookResult = componentClient.forEventSourcedEntity(slot)
                .method(BookingSlotEntity::bookSlot)
                .invoke(bookCommand);

        assertEquals(done(), bookResult);

        sleep(5);

        participantList.forEach(p -> {
            var slotsList = querySlotsByParticipantIdAndStatus(p.id(), "booked").slots();
            assertEquals(1, slotsList.size());

            var slotRow = slotsList.getFirst();
            assertEquals(slot, slotRow.slotId());
            assertEquals(p.id(), slotRow.participantId());
            assertEquals(p.participantType().name(), slotRow.participantType());
            assertEquals(bookingId, slotRow.bookingId());
        });
    }

    @Test
    public void testQueryParticipantCancelBookingTest() {
        var slot = generateSlot();
        var bookingId = generateBooking();

        participantList.forEach(p -> {
            var command = new BookingSlotEntity.Command.MarkSlotAvailable(p);
            var result = componentClient.forEventSourcedEntity(slot)
                    .method(BookingSlotEntity::markSlotAvailable)
                    .invoke(command);

            assertEquals(done(), result);
        });

        var bookCommand = new BookingSlotEntity.Command.BookReservation(studentParticipant.id(),
                aircraftParticipant.id(), instructorParticipant.id(), bookingId);
        var bookResult = componentClient.forEventSourcedEntity(slot)
                .method(BookingSlotEntity::bookSlot)
                .invoke(bookCommand);

        assertEquals(done(), bookResult);

        var cancelBookingResult = componentClient.forEventSourcedEntity(slot)
                .method(BookingSlotEntity::cancelBooking)
                .invoke(bookingId);

        assertEquals(done(), cancelBookingResult);

        sleep(10);

        participantList.forEach(p -> {
            var slotsList = querySlotsByParticipantIdAndStatus(p.id(), "canceled").slots().stream()
                    .filter(s -> s.slotId().equals(slot)).toList();
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
            System.out.println("################");
            System.out.printf("Sleeping for %d seconds%n", seconds);
            System.out.println("################");
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String generateSlot() {
        return "bestSlot-" + UUID.randomUUID();
    }

    private String generateBooking() {
        return "booking-" + UUID.randomUUID();
    }
}
