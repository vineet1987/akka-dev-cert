package io.example.api;

import akka.javasdk.http.HttpResponses;
import akka.javasdk.testkit.TestKitSupport;
import io.example.application.ParticipantSlotsView;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightEndpointTest extends TestKitSupport {

    private final Logger log = LoggerFactory.getLogger(FlightEndpointTest.class);

    static Participant studentParticipant = new Participant("alice", Participant.ParticipantType.STUDENT);
    static Participant aircraftParticipant = new Participant("superplane", Participant.ParticipantType.AIRCRAFT);
    static Participant instructorParticipant = new Participant("superteacher", Participant.ParticipantType.INSTRUCTOR);

    static List<Participant> participantList = List.of(studentParticipant, aircraftParticipant, instructorParticipant);


    @Test
    public void testPostMarkAvailable() {
        String slot = generateSlot();
        var response = httpClient.POST("/flight/availability/" + slot).withRequestBody(
                new FlightEndpoint.AvailabilityRequest(studentParticipant.id(), studentParticipant.participantType().name())
        ).invoke();
        assertTrue(response.status().isSuccess());

        checkTimeSlot(slot, 0, 1);

        sleep(10);

        var slotsByStatusResponse = httpClient.GET("/flight/slots/" + studentParticipant.id() + "/" + "available")
                .responseBodyAs(ParticipantSlotsView.SlotList.class).invoke();

        var slotRow = slotsByStatusResponse.body().slots().stream()
                .filter(s -> s.slotId().equals(slot)).toList().getFirst();

        assertEquals(slotRow.slotId(), slot);
        assertEquals(slotRow.participantId(), studentParticipant.id());
        assertEquals(slotRow.participantType(), Participant.ParticipantType.STUDENT.name());
    }

    @Test
    public void testPostUnMarkAvailable() {
        String slot = generateSlot();

        //Mark Available
        {
            var response = httpClient.POST("/flight/availability/" + slot).withRequestBody(
                    new FlightEndpoint.AvailabilityRequest(studentParticipant.id(), studentParticipant.participantType().name())
            ).invoke();
            assertTrue(response.status().isSuccess());
            checkTimeSlot(slot, 0, 1);

            sleep(10);

            var getSlotListResponse = httpClient.GET("/flight/slots/" + studentParticipant.id() + "/" + "available")
                    .responseBodyAs(ParticipantSlotsView.SlotList.class).invoke();
            assertEquals(1, getSlotListResponse.body().slots().size());
        }

        //Unmark Available
        {
            var unmarkAvailableResponse = httpClient.DELETE("/flight/availability/" + slot).withRequestBody(
                    new FlightEndpoint.AvailabilityRequest(studentParticipant.id(), studentParticipant.participantType().name())
            ).invoke();
            assertTrue(unmarkAvailableResponse.status().isSuccess());

            checkTimeSlot(slot, 0, 0);

            sleep(10);

            var getSlotListResponseAfterUnmarkedAvailable = httpClient.GET("/flight/slots/" + studentParticipant.id() + "/" + "available")
                    .responseBodyAs(ParticipantSlotsView.SlotList.class).invoke();

            assertTrue(getSlotListResponseAfterUnmarkedAvailable.body().slots().isEmpty());
        }
    }


    @Test
    public void testPostMarkAvailableShouldFailForInvalidParticipantType() {
        String slot = generateSlot();

        var response = httpClient.POST("/flight/availability/" + slot).withRequestBody(
                new FlightEndpoint.AvailabilityRequest("alice", "randomParticipantType")
        ).invoke();
        assertTrue(response.status().isFailure());

        checkTimeSlot(slot, 0, 0);
    }

    @Test
    public void testPostUnmarkAvailableShouldFailForInvalidParticipantType() {
        String slot = generateSlot();

        var response = httpClient.DELETE("/flight/availability/" + slot).withRequestBody(
                new FlightEndpoint.AvailabilityRequest("alice", "randomParticipantType")
        ).invoke();
        assertTrue(response.status().isFailure());
    }


    @Test
    public void testCreateBooking() {
        String slot = generateSlot();
        String bookingId = generateBookingId();

        participantList.forEach(p -> {
            var response = httpClient.POST("/flight/availability/" + slot).withRequestBody(
                    new FlightEndpoint.AvailabilityRequest(p.id(), p.participantType().name())
            ).invoke();
            assertTrue(response.status().isSuccess());
        });

        var createBookingResponse = httpClient.POST("/flight/bookings/" + slot).withRequestBody(
                new FlightEndpoint.BookingRequest(studentParticipant.id(),
                        aircraftParticipant.id(),
                        instructorParticipant.id(),
                        bookingId)).invoke();

        var createBookingResponseStatus = createBookingResponse.status();

        assertTrue(createBookingResponseStatus.isSuccess());
        assertEquals(HttpResponses.created().status().intValue(), createBookingResponseStatus.intValue());

        checkTimeSlot(slot, 3, 0);

        sleep(10);

        participantList.forEach(p -> {
            var slotsByStatusResponse = httpClient.GET("/flight/slots/" + p.id() + "/" + "booked")
                    .responseBodyAs(ParticipantSlotsView.SlotList.class).invoke();

            var slotRow = slotsByStatusResponse.body().slots().stream()
                    .filter(s -> s.slotId().equals(slot)).toList().getFirst();
            ;

            assertEquals(slotRow.slotId(), slot);
            assertEquals(slotRow.participantId(), p.id());
            assertEquals(slotRow.participantType(), p.participantType().name());
        });

    }

    @Test
    public void testReBookingSameSlotShouldFail() {
        String slot = generateSlot();
        String bookingId = generateBookingId();

        participantList.forEach(p -> {
            var response = httpClient.POST("/flight/availability/" + slot).withRequestBody(
                    new FlightEndpoint.AvailabilityRequest(p.id(), p.participantType().name())
            ).invoke();
            assertTrue(response.status().isSuccess());
        });

        var bookingRequestBody = new FlightEndpoint.BookingRequest(studentParticipant.id(),
                aircraftParticipant.id(),
                instructorParticipant.id(),
                bookingId);

        var createBookingResponse = httpClient.POST("/flight/bookings/" + slot).withRequestBody(bookingRequestBody).invoke();
        assertTrue(createBookingResponse.status().isSuccess());
        checkTimeSlot(slot, 3, 0);

        String reBookingId = generateBookingId();
        var reBookingRequestBody = new FlightEndpoint.BookingRequest(studentParticipant.id(),
                aircraftParticipant.id(),
                instructorParticipant.id(),
                reBookingId);

        var reBookingResponse = httpClient.POST("/flight/bookings/" + slot).withRequestBody(reBookingRequestBody).invoke();
        assertTrue(reBookingResponse.status().isFailure());
        checkTimeSlot(slot, 3, 0);
    }

    @Test
    public void testCancelBooking() {
        String slot = generateSlot();
        String bookingId = generateBookingId();

        participantList.forEach(p -> {
            var response = httpClient.POST("/flight/availability/" + slot).withRequestBody(
                    new FlightEndpoint.AvailabilityRequest(p.id(), p.participantType().name())
            ).invoke();
            assertTrue(response.status().isSuccess());
        });

        var createBookingResponse = httpClient.POST("/flight/bookings/" + slot).withRequestBody(
                new FlightEndpoint.BookingRequest(studentParticipant.id(),
                        aircraftParticipant.id(),
                        instructorParticipant.id(),
                        bookingId)).invoke();

        assertTrue(createBookingResponse.status().isSuccess());
        checkTimeSlot(slot, 3, 0);

        var cancelBookingResponse = httpClient.DELETE("/flight/bookings/" + slot + "/" + bookingId).invoke();
        assertTrue(cancelBookingResponse.status().isSuccess());
        // After cancelling the participants do not become available as mentioned in the Timeslot::cancelBooking method
        checkTimeSlot(slot, 0, 0);
    }

    private void checkTimeSlot(String slotId, int expectedBookingsCount, int expectAvailableCount) {
        var timeSlotResponse = httpClient.GET("/flight/availability/" + slotId).responseBodyAs(Timeslot.class).invoke();

        assertEquals(expectedBookingsCount, timeSlotResponse.body().bookings().size());
        assertEquals(expectAvailableCount, timeSlotResponse.body().available().size());
    }

    private String generateSlot() {
        return "bestSlot-" + UUID.randomUUID();
    }

    private String generateBookingId() {
        return "bestbooking-" + UUID.randomUUID();
    }

    private void sleep(int seconds) {
        try {
            log.info("################");
            log.info("Sleeping for {} seconds", seconds);
            log.info("################");
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
