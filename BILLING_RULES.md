# VITALIS - how the money works (rules the program enforces)

## How a visit is paid (the Checkout)
One visit is paid **once**. The bill is the sum of lines, and **every amount is calculated by the program from the
database, nothing is typed**:

```
Bill total = doctor's fee            (doctors.consultation_fee of the visit's doctor)
           + room                    (days in the room x the price per day of the room, only for an inpatient)
           + medicines               (price x quantity of each medicine the cashier adds)
```
1. Reception > **Checkout** > choose the **visit**. The bill appears with every line and the total.
2. Add the **medicines** the patient buys (item + quantity > *Add medicine*). The stock is shown in the list.
3. Choose the **payment method** and press **Pay and show receipt**.
4. If the patient is still **Admitted**, the same payment **discharges** the patient: room days are counted until now.

Each line is saved as a row of `billings` with a `bill_type` (Service / Room / Medicine); the rows of one visit share the
`appointment_id`. Days = the calendar days between admission and discharge, at least 1.

Which visits can be paid: the doctor completed it (*Completed*), the patient was discharged (*Discharged*) or the patient
is *Admitted*. A *Scheduled* or *Cancelled* visit cannot be paid.

## The receipt
After the payment a receipt window opens: receipt number, date, patient, who served, **every line**, total, payment
method. **Print** sends it to a printer (for example Microsoft Print to PDF). **Show receipt** above the table opens the
receipt of the whole visit again, from any of its lines.

## Rules (checked in `BillingDAO`, so they hold even if a screen is changed)
| Rule | What happens |
|---|---|
| A visit can be paid only once | "This visit was already paid (bill #n)" |
| The payment method must be one of Cash, Card, Bank Transfer, Insurance, Other | refused |
| The amounts are read from the database (fee, room price, medicine price) | the screen cannot send a different price |
| A medicine that has expired cannot be sold | refused, nothing saved |
| A room cannot be sold as a medicine | refused |
| Not enough stock, or a quantity of 0 or less | refused, nothing saved |
| The lines, the stock and the discharge change together | one database transaction: all or nothing |
| Two lines of the same medicine are added together against the stock | rolled back when the total is too much |
| The total must be above 0 and below 100,000,000 | refused ("nothing to charge" for a visit without fee, room or medicine) |
| A bill cannot be deleted or changed after the payment | there is no such function (the owner only looks at the bills) |
| Only the reception takes payments (the owner, admin, doctor, nurse cannot) | needs the `BILL_CREATE` permission |

Old bills that were still *Pending* before the checkout existed can be paid with **Mark old bill paid**.

## Where the numbers come from (Reports, owner only)
* **Income** = the sum of the **Paid** bill lines of the month (created date).
* **Expenses** = **salaries** (the monthly salaries of all *active* staff, `users.monthly_salary`; a dismissed person is
  not counted) + **medicine cost** (quantity sold x `inventory.unit_cost` of each medicine sold in that month).
* **Profit** = income - expenses.
* **Medicine usage** = for each medicine the quantity sold, what it brought in and what it cost (this month or all time).
* The chart shows income, salaries and medicine cost for the current month and the five months before.

## Restocking
Inventory staff press **Add to stock** with the delivered quantity (and optionally the cost per unit of that delivery,
which replaces `unit_cost`). The medicine cost in the report uses the cost that is saved when the medicine is sold.

## Not included (be ready to say this)
* No partial payment, refund, discount or tax: a visit is paid in full at once.
* Income is counted in the month the bill was created.
* The salary of past months is not stored: every month of the chart uses today's monthly salaries of the active staff.
* The money spent on **buying** stock is not stored with a date (that needs a purchase table); the expense counts the
  *cost of the medicines that were used* instead. The delivery cost only updates `unit_cost`.
* A room is charged per started calendar day.
* Prices are in USD (`$`).
