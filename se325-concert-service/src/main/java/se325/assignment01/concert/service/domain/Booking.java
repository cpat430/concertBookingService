package se325.assignment01.concert.service.domain;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a booking.
 * bookingId   the id of the booking which is generated when a row is added to a column
 * concertId   the id of the concert which was booked
 * date        the date on which that concert was booked
 * seats       the seats which were booked for that concert on that date
 */
@Entity
@Table(name = "BOOKING")
public class Booking {

    @Id
    @GeneratedValue
    private long bookingId;
    private long concertId;
    private LocalDateTime date;
    private String uuid;

    @OneToMany(fetch = FetchType.EAGER, cascade = {CascadeType.ALL, CascadeType.REMOVE})
    private Set<Seat> seats = new HashSet<>();

    public Booking() {
    }

    public Booking(long concertId, LocalDateTime date, Set<Seat> seats) {
        this.concertId = concertId;
        this.date = date;
        this.seats = seats;
    }

    public long getConcertId() {
        return concertId;
    }

    public void setConcertId(long concertId) {
        this.concertId = concertId;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public Set<Seat> getSeats() {
        return seats;
    }

    public void setSeats(Set<Seat> seats) {
        this.seats = seats;
    }

    public long getBookingId() {
        return this.bookingId;
    }

    public String getUuid() {
        return this.uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
