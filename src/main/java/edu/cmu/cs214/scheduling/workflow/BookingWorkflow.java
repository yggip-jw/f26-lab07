package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.BookingType;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import java.util.Map;

/**
 * The front door of the scheduler. Validates common inputs and delegates
 * type-specific operations to polymorphic booking behaviors.
 */
public class BookingWorkflow {

    private final BookingStore store;
    private final Map<BookingType, BookingBehavior> behaviors;

    public BookingWorkflow(BookingStore store, PriceCalculator calculator, NotificationHub hub) {
        if (store == null || calculator == null || hub == null) {
            throw new IllegalArgumentException("workflow collaborators must not be null");
        }
        this.store = store;
        this.behaviors = Map.of(
                BookingType.REGULAR, new RegularBookingBehavior(store, calculator, hub),
                BookingType.RECURRING, new RecurringBookingBehavior(store, calculator, hub),
                BookingType.BLOCKED, new BlockedBookingBehavior(store, calculator, hub));
    }

    /**
     * Validates a request, writes what it can, and reports what it did.
     *
     * @return an outcome naming every booking written and every slot passed over
     */
    public BookingOutcome submit(BookingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        Room room = store.findRoom(request.roomId());
        if (room == null) {
            return BookingOutcome.rejected("unknown room " + request.roomId());
        }

        return behaviors.get(request.type()).submit(request, room);
    }

    /**
     * Releases a booking.
     *
     * @param adminOverride set by callers acting with facilities authority
     * @return true when something was released
     */
    public boolean cancel(long bookingId, boolean adminOverride) {
        Booking booking = store.findBooking(bookingId);
        if (booking == null || booking.isCancelled()) {
            return false;
        }
        Room room = store.findRoom(booking.getRoomId());
        String roomName = room == null ? booking.getRoomId() : room.getName();

        return behaviors.get(booking.getType()).cancel(booking, adminOverride, roomName);
    }

    /** What the holder owes for a booking, in dollars. */
    public double priceOf(long bookingId) {
        Booking booking = store.findBooking(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("unknown booking " + bookingId);
        }

        return behaviors.get(booking.getType()).priceOf(booking);
    }

    /** A one-line summary for schedules and confirmation screens. */
    public String describe(long bookingId) {
        Booking booking = store.findBooking(bookingId);
        if (booking == null) {
            return "Unknown booking #" + bookingId;
        }
        Room room = store.findRoom(booking.getRoomId());
        String roomName = room == null ? booking.getRoomId() : room.getName();

        return behaviors.get(booking.getType()).describe(booking, roomName);
    }
}
