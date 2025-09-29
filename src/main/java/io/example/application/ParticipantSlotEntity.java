package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.example.domain.Participant.ParticipantType;

@ComponentId("participant-slot")
public class ParticipantSlotEntity
        extends EventSourcedEntity<ParticipantSlotEntity.State, ParticipantSlotEntity.Event> {

    public Effect<Done> unmarkAvailable(Commands.UnmarkAvailable unmark) {
        return effects().persist(
                new Event.UnmarkedAvailable(
                        unmark.slotId(),
                        unmark.participantId(), unmark.participantType
                )
        ).thenReply(newState -> Done.done());
    }

    public Effect<Done> markAvailable(Commands.MarkAvailable mark) {
        return effects().persist(
                new Event.MarkedAvailable(
                        mark.slotId,
                        mark.participantId, mark.participantType
                )
        ).thenReply(newState -> Done.done());
    }

    public Effect<Done> book(Commands.Book book) {
        if (currentState() == emptyState() || !"available".equals(currentState().status)) {
            return effects().error("Cannot book slot with participant ID: " + book.participantId + " and " +
                    "participant type: " + book.participantType);
        }
        return effects().persist(
                new Event.Booked(
                        book.slotId,
                        book.participantId, book.participantType,
                        book.bookingId
                )
        ).thenReply(newState -> Done.done());
    }

    public Effect<Done> cancel(Commands.Cancel cancel) {
        return effects().persist(
                new Event.Canceled(
                        cancel.slotId,
                        cancel.participantId(), cancel.participantType,
                        cancel.bookingId
                )
        ).thenReply(newState -> Done.done());
    }

    record State(
            String slotId, String participantId, ParticipantType participantType, String status) {
    }

    public sealed interface Commands {
        record MarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record UnmarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record Book(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }

        record Cancel(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }
    }

    public sealed interface Event {
        @TypeName("marked-available")
        record MarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("unmarked-available")
        record UnmarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("participant-booked")
        record Booked(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }

        @TypeName("participant-canceled")
        record Canceled(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }
    }

    @Override
    public State applyEvent(Event event) {
        return switch (event) {
            case Event.Booked booked -> new State(
                    booked.slotId, booked.participantId, booked.participantType, "booked"
            );
            case Event.Canceled canceled -> new State(
                    canceled.slotId, canceled.participantId, canceled.participantType, ""
            );
            case Event.MarkedAvailable markedAvailable -> new State(
                    markedAvailable.slotId, markedAvailable.participantId, markedAvailable.participantType, "available"
            );
            case Event.UnmarkedAvailable markedUnavailable -> new State(
                    markedUnavailable.slotId, markedUnavailable.participantId, markedUnavailable.participantType, ""
            );
        };
    }
}
