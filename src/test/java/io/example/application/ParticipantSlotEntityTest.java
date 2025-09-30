package io.example.application;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.domain.Participant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.*;

class ParticipantSlotEntityTest {

    static List<Participant> participantList = List.of(
            new Participant("alice", Participant.ParticipantType.STUDENT),
            new Participant("superplane", Participant.ParticipantType.AIRCRAFT),
            new Participant("superteacher", Participant.ParticipantType.INSTRUCTOR)
    );

    @Test
    void testMarkSlotAvailable() {
        var slot = "bestslot";

        participantList.forEach(participant -> {
            var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
            var participantSlotId = slot + "-" + participant.id();

            {
                var command = new ParticipantSlotEntity.Commands.MarkAvailable(participantSlotId, participant.id(), participant.participantType());
                var result = testKit.method(ParticipantSlotEntity::markAvailable).invoke(command);

                assertTrue(result.isReply());
                assertEquals(done(), result.getReply());

                var event = result.getNextEventOfType(ParticipantSlotEntity.Event.MarkedAvailable.class);
                assertEquals(participantSlotId, event.slotId());
                assertEquals(participant.id(), event.participantId());
                assertEquals(participant.participantType(), event.participantType());
            }

            {
                var state = testKit.getState();
                assertEquals(participantSlotId, state.slotId());
                assertEquals(participant.id(), state.participantId());
                assertEquals(participant.participantType(), state.participantType());
                assertEquals("available", state.status());
            }
        });
    }

    @Test
    void testMarkSlotUnavailable() {
        var slot = "bestslot";

        participantList.forEach(participant -> {
            var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
            var participantSlotId = markParticipantSlotAvailable(participant, slot, testKit);

            {
                var command = new ParticipantSlotEntity.Commands.UnmarkAvailable(participantSlotId, participant.id(), participant.participantType());
                var result = testKit.method(ParticipantSlotEntity::unmarkAvailable).invoke(command);

                assertTrue(result.isReply());
                assertEquals(done(), result.getReply());

                var event = result.getNextEventOfType(ParticipantSlotEntity.Event.UnmarkedAvailable.class);
                assertEquals(participantSlotId, event.slotId());
                assertEquals(participant.id(), event.participantId());
                assertEquals(participant.participantType(), event.participantType());
            }

            {
                var state = testKit.getState();
                assertEquals(participantSlotId, state.slotId());
                assertEquals(participant.id(), state.participantId());
                assertEquals(participant.participantType(), state.participantType());
                assertTrue(state.status().isEmpty());
            }
        });
    }

    @Test
    void bookParticipantSlot() {
        var slot = "bestslot";
        var bookingId = "bestbooking";

        participantList.forEach(participant -> {
            var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
            var participantSlotId = markParticipantSlotAvailable(participant, slot, testKit);

            {
                var command = new ParticipantSlotEntity.Commands.Book(participantSlotId, participant.id(), participant.participantType(), bookingId);
                var result = testKit.method(ParticipantSlotEntity::book).invoke(command);

                assertTrue(result.isReply());
                assertEquals(done(), result.getReply());

                var event = result.getNextEventOfType(ParticipantSlotEntity.Event.Booked.class);
                assertEquals(participantSlotId, event.slotId());
                assertEquals(participant.id(), event.participantId());
                assertEquals(participant.participantType(), event.participantType());
                assertEquals(bookingId, event.bookingId());
            }

            {
                var state = testKit.getState();
                assertEquals(participantSlotId, state.slotId());
                assertEquals(participant.id(), state.participantId());
                assertEquals(participant.participantType(), state.participantType());
                assertEquals("booked", state.status());
            }
        });
    }

    @Test
    void testErrorWhenBookingEmptySlot() {
        var slot = "bestslot";
        var bookingId = "bestbooking";

        participantList.forEach(participant -> {
            var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
            var participantSlotId = slot + "-" + participant.id();


            var command = new ParticipantSlotEntity.Commands.Book(participantSlotId, participant.id(), participant.participantType(), bookingId);
            var result = testKit.method(ParticipantSlotEntity::book).invoke(command);

            assertFalse(result.isReply());
            assertTrue(result.getAllEvents().isEmpty());
            assertNull(testKit.getState());
        });
    }

    @Test
    void testErrorWhenBookingUnavailableSlot() {
        var slot = "bestslot";
        var bookingId = "bestbooking";

        participantList.forEach(participant -> {
            var testKit = EventSourcedTestKit.of(ParticipantSlotEntity::new);
            var participantSlotId = markParticipantSlotAvailable(participant, slot, testKit);
            var command = new ParticipantSlotEntity.Commands.UnmarkAvailable(participantSlotId, participant.id(), participant.participantType());
            var result = testKit.method(ParticipantSlotEntity::unmarkAvailable).invoke(command);
            assertTrue(result.isReply());

            var bookCommand = new ParticipantSlotEntity.Commands.Book(participantSlotId, participant.id(), participant.participantType(), bookingId);
            var bookResult = testKit.method(ParticipantSlotEntity::book).invoke(bookCommand);

            assertFalse(bookResult.isReply());
            assertTrue(bookResult.getAllEvents().isEmpty());

            var state = testKit.getState();
            assertEquals(participant.id(), state.participantId());
            assertEquals(participant.participantType(), state.participantType());
            assertTrue(state.status().isEmpty());
        });
    }

    private static String markParticipantSlotAvailable(Participant participant,
                                                       String slotId,
                                                       EventSourcedTestKit<ParticipantSlotEntity.State, ParticipantSlotEntity.Event,
                                                               ParticipantSlotEntity> testKit) {
        var participantSlotId = slotId + "-" + participant.id();
        var command = new ParticipantSlotEntity.Commands.MarkAvailable(participantSlotId, participant.id(), participant.participantType());
        var result = testKit.method(ParticipantSlotEntity::markAvailable).invoke(command);


        var event = result.getNextEventOfType(ParticipantSlotEntity.Event.MarkedAvailable.class);
        return event.slotId();
    }
}
