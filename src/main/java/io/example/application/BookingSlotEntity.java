package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static akka.Done.done;

@ComponentId("booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        return effects().persist(
                new BookingEvent.ParticipantMarkedAvailable(
                        entityId,
                        cmd.participant().id(),
                        cmd.participant().participantType()
                )
        ).thenReply(newState -> done());
    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        if (!currentState().isWaiting(cmd.participant().id(), cmd.participant().participantType())) {
            return effects().error(String.format("Participant with Id: %1$s and type: %2$s is not available",
                    cmd.participant().id(), cmd.participant().participantType()));
        }

        return effects().persist(
                new BookingEvent.ParticipantUnmarkedAvailable(
                        entityId,
                        cmd.participant().id(),
                        cmd.participant().participantType()
                )
        ).thenReply(newState -> done());
    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {
        if (!currentState().isBookable(cmd.studentId, cmd.aircraftId, cmd.instructorId)) {
            var errors = validateParticipantAvailability(cmd);
            var errorString = String.join(", ", errors);
            logger.warn("Booking error: {}", errorString);
            return effects().error(String.join(", ", errors));
        }
        return effects().persistAll(
                List.of(
                        new BookingEvent.ParticipantBooked(
                                entityId,
                                cmd.studentId,
                                Participant.ParticipantType.STUDENT,
                                cmd.bookingId),
                        new BookingEvent.ParticipantBooked(
                                entityId,
                                cmd.aircraftId,
                                Participant.ParticipantType.AIRCRAFT,
                                cmd.bookingId),
                        new BookingEvent.ParticipantBooked(
                                entityId,
                                cmd.instructorId,
                                Participant.ParticipantType.INSTRUCTOR,
                                cmd.bookingId))
        ).thenReply(newState -> done());
    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {
        return effects().persistAll(currentState().findBooking(bookingId)
                        .stream()
                        .map(booking -> {
                            var participant = booking.participant();
                            return new BookingEvent.ParticipantCanceled(entityId,
                                    participant.id(),
                                    participant.participantType(),
                                    booking.bookingId());

                        })
                        .collect(Collectors.toList()))
                .thenReply(newState -> done());

    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
    }

    @Override
    public Timeslot emptyState() {
        return new Timeslot(
                // NOTE: these are just estimates for capacity based on it being a sample
                HashSet.newHashSet(10), HashSet.newHashSet(10));
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantBooked participantBooked -> currentState().book(participantBooked);
            case BookingEvent.ParticipantCanceled participantCanceled ->
                    currentState().cancelBooking(participantCanceled.bookingId());
            case BookingEvent.ParticipantMarkedAvailable participantMarkedAvailable ->
                    currentState().reserve(participantMarkedAvailable);
            case BookingEvent.ParticipantUnmarkedAvailable participantUnmarkedAvailable ->
                    currentState().unreserve(participantUnmarkedAvailable);

        };
    }

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }

    private ArrayList<String> validateParticipantAvailability(Command.BookReservation cmd) {
        var errors = new ArrayList<String>();

        if (!currentState().isWaiting(cmd.studentId, Participant.ParticipantType.STUDENT))
            errors.add(String.format("[Participant with Id: %1$s and type: %2$s is not available]", cmd.studentId,
                    Participant.ParticipantType.STUDENT));

        if (!currentState().isWaiting(cmd.aircraftId, Participant.ParticipantType.AIRCRAFT))
            errors.add(String.format("[Participant with Id: %1$s and type: %2$s is not available]", cmd.aircraftId,
                    Participant.ParticipantType.AIRCRAFT));

        if (!currentState().isWaiting(cmd.instructorId, Participant.ParticipantType.INSTRUCTOR))
            errors.add(String.format("[Participant with Id: %1$s and type: %2$s is not available]", cmd.instructorId,
                    Participant.ParticipantType.INSTRUCTOR));
        return errors;
    }
}
