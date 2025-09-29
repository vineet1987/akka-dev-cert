package io.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("view-participant-slots")
public class ParticipantSlotsView extends View {

    private static Logger logger = LoggerFactory.getLogger(ParticipantSlotsView.class);

    @Consume.FromEventSourcedEntity(ParticipantSlotEntity.class)
    public static class ParticipantSlotsViewUpdater extends TableUpdater<SlotRow> {

        public Effect<SlotRow> onEvent(ParticipantSlotEntity.Event event) {
            return switch (event) {
                case ParticipantSlotEntity.Event.MarkedAvailable markedAvailable ->
                        effects().updateRow(onEvent(markedAvailable));
                case ParticipantSlotEntity.Event.UnmarkedAvailable unMarkedAvailable ->
                        effects().updateRow(onEvent(unMarkedAvailable));
                case ParticipantSlotEntity.Event.Booked booked ->
                        effects().updateRow(onEvent(booked));
                case ParticipantSlotEntity.Event.Canceled canceled ->
                        effects().updateRow(onEvent(canceled));
            };
        }

        SlotRow onEvent(ParticipantSlotEntity.Event.MarkedAvailable event) {
            return new SlotRow(
                    event.slotId(),
                    event.participantId(),
                    event.participantType().name(),
                    "",
                    "available"
            );
        }

        SlotRow onEvent(ParticipantSlotEntity.Event.UnmarkedAvailable event) {
            return new SlotRow(
                    event.slotId(),
                    event.participantId(),
                    event.participantType().name(),
                    "",
                    "unavailable"
            );
        }

        SlotRow onEvent(ParticipantSlotEntity.Event.Booked event) {
            return new SlotRow(
                    event.slotId(),
                    event.participantId(),
                    event.participantType().name(),
                    event.bookingId(),
                    "booked"
            );
        }

        SlotRow onEvent(ParticipantSlotEntity.Event.Canceled event) {
            return new SlotRow(
                    event.slotId(),
                    event.participantId(),
                    event.participantType().name(),
                    event.bookingId(),
                    "canceled"
            );
        }
    }


    public record SlotRow(
            String slotId,
            String participantId,
            String participantType,
            String bookingId,
            String status) {
    }

    public record ParticipantStatusInput(String participantId, String status) {
    }

    public record SlotList(List<SlotRow> slots) {
    }

    @Query("""
            SELECT * as slots
              FROM slots
              WHERE participantId = :participantId
            """)
    public QueryEffect<SlotList> getSlotsByParticipant(String participantId) {
        return queryResult();
    }

    @Query("""
            SELECT * as slots
              FROM slots
              WHERE participantId = :participantId
              AND status = :status
            """)
    public QueryEffect<SlotList> getSlotsByParticipantAndStatus(ParticipantStatusInput input) {
        return queryResult();
    }
}
