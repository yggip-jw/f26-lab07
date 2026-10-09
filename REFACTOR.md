# REFACTOR.md

One section per milestone. Fill each one in as you go, in order.

Milestone 1 is written in two sittings, the pin before the refactor and the
rest after. A pin written afterwards is worth nothing, and a TA will ask.

Keep it short and specific. Point at methods, call sites, and test names.

---

## Milestone 1: Direct a refactor, characterization first

### The pin (write this section before you direct the refactor)

**The pin.** 

`src/test/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflowTest.java`,
`recurringSubmitSkipsASlotThatStartsWhenAnotherEnds()`: `BookingWorkflow.submit()`
skips the first recurring slot when it starts exactly when an existing booking
ends, but stores and notifies for the free slot the following week.
Before refactoring, `mvn -B test` passed: 36 tests, 0 failures, 0 errors, 0 skipped.
No existing test method was edited or deleted.

**Why that one, and does a shipped test already cover it?** This boundary is
easy to change accidentally: regular bookings use strict overlap comparisons,
while recurring bookings include touching endpoints. Reviewing `BookingWorkflowTest`
showed that `regularSubmitAcceptsASlotThatStartsWhenAnotherEnds()` pins the regular
case, but no shipped test pins the recurring case. This test checks the exact
skipped slot, the successful next-week slot, the stored count, and the notification.

**What a regeneration would do differently here.** Regeneration would have to
choose whether touching endpoints count as a conflict. It would likely use the
same strict overlap rule for both types, accepting the first recurring slot
instead of skipping it and thereby adding a booking and a notification.

### The directive

**The refactor and the exact directive.** Replace Conditional with Polymorphism.
The implementation scope established in the conversation was to replace the
repeated type branches in `submit`, `cancel`, `priceOf`, and `describe` within
`workflow/`, preserving the public API and all existing behavior. Other business
packages and existing tests were out of bounds; `REFACTOR.md` records the work.
This boundary keeps the change focused on workflow dispatch without changing
the domain model, pricing, notification delivery, or reporting.

### The result

**The diff and the suite.** Show refactor commit `d3c1651` with
`git diff 3abde61 d3c1651`. `mvn -B test` passed the shipped 35 tests plus the pin:
`Tests run: 36, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`).

**What did NOT change: behavior and files.** The public API, validation,
returned messages, notification text/order, and store updates were preserved.
The pin confirms that recurring bookings still skip touching slots, although
regular bookings accept them. Recurring cancellation still cancels the selected
occurrence and later ones; recurring pricing still sums non-cancelled occurrences.
The agent compared the moved branches and checked the commit diff: only
`workflow/` and `REFACTOR.md` changed. Tests, `domain/`, `notify/`, `pricing/`,
and `reporting/` are unchanged. No out-of-scope edits were found.


### The closing explanation

**Refactor or regenerate?** Refactor. Test coverage: the 35 shipped tests leave
behavior gaps, as the new boundary pin demonstrates, so green tests alone would
not establish that regenerated code is equivalent. Code age: this is a fresh,
agent-generated starter, making regeneration plausible, but its existing behavior
already matters. Spec quality: comments and the README do not fully specify
details such as the different overlap boundaries. Reach: the workflow controls
storage, notifications, cancellation, pricing, and descriptions, so replacing its
logic could affect several observable results. Moving the existing branches into
polymorphic implementations addresses the repeated dispatch with less uncertainty.

**What would flip your answer.** A complete executable specification covering
boundary rules, cancellation, pricing, exact messages, and side effects, combined
with a requirements change that invalidates most existing workflow logic, would
make regeneration a better option.

---

## Milestone 2: The pattern critique

### The patterns present

- Factory: `NotifierFactory.createStrategy()` (not a subclass-based GoF Factory Method).
- Strategy: `NotificationStrategy` and `EmailNotificationStrategy`, used by `NotificationHub`.
- Observer: `NotificationHub`, `NotificationSubscriber`, and `OutboxSubscriber`.
- Adapter: `OutboxSubscriber` translates `onNotification(String)` into `Outbox.append(String)`.

### The problem each one solves


- Factory: callers need renderer creation selected or configured in one place.
- Strategy: the same publishing operation needs interchangeable formatting algorithms.
- Observer: independent recipients must receive each publication without the publisher naming each recipient.
- Adapter: an existing destination with a different API must participate as a subscriber.

### Which of those problems exist here

- Factory: `createStrategy()` always returns `new EmailNotificationStrategy()` with no selection or setup.
- Strategy: only one implementation exists, and the hub always obtains it from that fixed factory.

### The simpler structure

**Your proposal.** Keep `NotificationMessage`, `Outbox`, and a concrete
`NotificationHub` holding an outbox. Remove the factory, formatting strategy,
and subscriber layers. The key method becomes:

```java
public void publish(NotificationMessage message) {
    outbox.append("To: " + message.recipient()
            + " | Subject: " + message.subject()
            + " | " + message.body());
}
```

**What stays the same.** Each publication appends exactly one fully rendered
message in order, using the same recipient, subject, and body format.
`publishedMessageLandsInTheOutboxFullyRendered()` and
`aConfirmationFromTheWorkflowReachesTheOutbox()` pin that output; workflow tests
also check notification counts. Keep message validation and outbox access.
The shipped `hubDeliversToItsOneSubscriber()` and `factoryHandsBackTheSameInstance()`
assert the current structure, so removing those APIs would not pass those tests
unchanged. This is a proposal only: no notification code or tests are modified
in this lab.

**What you would keep, if anything.** No notification interface is needed for
the current single format and single destination. Keep the message record and
outbox because they represent the data and observable output.

### What would bring each layer back

- Strategy: callers must choose email or SMS formatting for each configured channel.
- Factory: configuration must select among renderers with different construction dependencies.
- Observer: every notification must reach both an outbox and an independently registered audit listener.
- Adapter: an unmodifiable external destination exposing `write(String)` must join those subscribers.
- Singleton: a process-wide SDK resource must have exactly one owner and all factories must share it; even then, explicit shared dependency injection deserves consideration.

**Misuse or anti-pattern?** This is misuse of otherwise useful patterns:
several layers address requirements absent here. If repeatedly applied as a
solution regardless of requirements, it becomes an overengineering anti-pattern.
The distinction matters because these patterns remain appropriate when the
specific requirements above actually appear.

---

## Milestone 3: The missing pattern

**The pattern.** Decorator fits `PriceCalculator` because weekend surcharges,
long-booking discounts, and membership discounts are composable adjustments
to a base price that could be layered independently, with rounding kept at the end.

**Would you apply it today?** No: the three adjustments and their order are fixed,
so the current method is clearer than introducing a decorator class for each rule.
