package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

/** Type-specific booking operations, selected by the workflow. */
abstract class BookingBehavior {
    protected static final String FACILITIES_CONTACT = "facilities@rooms.example.edu";

    protected final BookingStore store;
    protected final PriceCalculator calculator;
    protected final NotificationHub hub;

    BookingBehavior(BookingStore store, PriceCalculator calculator, NotificationHub hub) {
        this.store = store;
        this.calculator = calculator;
        this.hub = hub;
    }

    abstract BookingOutcome submit(BookingRequest request, Room room);

    abstract boolean cancel(Booking booking, boolean adminOverride, String roomName);

    abstract double priceOf(Booking booking);

    abstract String describe(Booking booking, String roomName);

    protected static String recipientFor(Member member) {
        return member == null ? FACILITIES_CONTACT : member.getEmail();
    }
}
