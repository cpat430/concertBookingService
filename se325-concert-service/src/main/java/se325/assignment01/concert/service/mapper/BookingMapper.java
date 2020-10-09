package se325.assignment01.concert.service.mapper;
//
import se325.assignment01.concert.common.dto.BookingDTO;
import se325.assignment01.concert.common.dto.SeatDTO;
import se325.assignment01.concert.service.domain.Booking;
import se325.assignment01.concert.service.domain.Seat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BookingMapper {

    public static BookingDTO toBookingDTO(Booking booking) {

        // convert the set seats to a list
        List<SeatDTO> dtoSeats = new ArrayList<SeatDTO>();
        Set<Seat> seats = booking.getSeats();

        // iterate through the seats
        for (Seat s : seats) {
            dtoSeats.add(SeatMapper.toSeatDto(s));
        }

        BookingDTO dtoBooking = new BookingDTO(
                // long concertId, LocalDateTime date, List<SeatDTO> seats
                booking.getConcertId(),
                booking.getDate(),
                dtoSeats
        );

        return dtoBooking;
    }
}
