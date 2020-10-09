package se325.assignment01.concert.service.mapper;

import se325.assignment01.concert.common.dto.SeatDTO;
import se325.assignment01.concert.service.domain.Seat;

public class SeatMapper {
//    String label, boolean isBooked, LocalDateTime date, BigDecimal price
    public static SeatDTO toSeatDto(Seat seat) {
        SeatDTO dtoSeat = new SeatDTO(
                seat.getLabel(),
                seat.getPrice()
        );

        return dtoSeat;
    }
}
