package io.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.domain.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This class is responsible for consuming events from the booking
// slot entity and turning those into command calls on the
// participant slot entity
@ComponentId("blooking-slot-consumer")
@Consume.FromEventSourcedEntity(BookingSlotEntity.class)
public class SlotToParticipantConsumer extends Consumer {

    private final ComponentClient client;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SlotToParticipantConsumer(ComponentClient client) {
        this.client = client;
    }

    public Effect onEvent(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantBooked e -> onEvent(e);
            case BookingEvent.ParticipantCanceled e -> onEvent(e);
            case BookingEvent.ParticipantMarkedAvailable e -> onEvent(e);
            case BookingEvent.ParticipantUnmarkedAvailable e -> onEvent(e);
        };
    }

    private Effect onEvent(BookingEvent.ParticipantBooked event) {
        logger.info("Booking participant with Id {} and Booking Id {}", event.participantId(), event.bookingId());
        var command = new ParticipantSlotEntity.Commands.Book(
                event.slotId(),
                event.participantId(),
                event.participantType(),
                event.bookingId());

        var participantSlot = client.forEventSourcedEntity(participantSlotId(event))
                .method(ParticipantSlotEntity::book)
                .invokeAsync(command);
        return effects().asyncDone(participantSlot);
    }

    private Effect onEvent(BookingEvent.ParticipantCanceled event) {
        logger.info("Canceling booking {} for participant {}", event.bookingId(), event.participantId());
        var command = new ParticipantSlotEntity.Commands.Cancel(
                event.slotId(),
                event.participantId(),
                event.participantType(),
                event.bookingId());

        var participantSlot = client.forEventSourcedEntity(participantSlotId(event))
                .method(ParticipantSlotEntity::cancel)
                .invokeAsync(command);
        return effects().asyncDone(participantSlot);
    }

    private Effect onEvent(BookingEvent.ParticipantMarkedAvailable event) {
        logger.info("Marking participant as available with Id {} and type {}", event.participantId(), event.participantType());
        var command = new ParticipantSlotEntity.Commands.MarkAvailable(
                event.slotId(),
                event.participantId(),
                event.participantType());

        var participantSlot = client.forEventSourcedEntity(participantSlotId(event))
                .method(ParticipantSlotEntity::markAvailable)
                .invokeAsync(command);
        return effects().asyncDone(participantSlot);
    }

    private Effect onEvent(BookingEvent.ParticipantUnmarkedAvailable event) {
        logger.info("UnMarking participant as available with Id {} and type {}", event.participantId(), event.participantType());
        var command = new ParticipantSlotEntity.Commands.UnmarkAvailable(
                event.slotId(),
                event.participantId(),
                event.participantType());

        var participantSlot = client.forEventSourcedEntity(participantSlotId(event))
                .method(ParticipantSlotEntity::unmarkAvailable)
                .invokeAsync(command);
        return effects().asyncDone(participantSlot);
    }

    // Participant slots are keyed by a derived key made up of
    // {slotId}-{participantId}
    // We don't need the participant type here because the participant IDs
    // should always be unique/UUIDs
    private String participantSlotId(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantBooked evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantUnmarkedAvailable evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantMarkedAvailable evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantCanceled evt -> evt.slotId() + "-" + evt.participantId();
        };
    }
}
