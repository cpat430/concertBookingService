package se325.assignment01.concert.service.mapper;

import se325.assignment01.concert.common.dto.ConcertDTO;
import se325.assignment01.concert.common.dto.ConcertSummaryDTO;
import se325.assignment01.concert.common.dto.PerformerDTO;
import se325.assignment01.concert.service.domain.Concert;
import se325.assignment01.concert.service.domain.Performer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ConcertMapper {

    public static ConcertSummaryDTO toConcertSummaryDto(Concert concert) {

        ConcertSummaryDTO concertSummaryDTO = new ConcertSummaryDTO(
                concert.getId(),
                concert.getTitle(),
                concert.getImageName()
        );

        return concertSummaryDTO;
    }

    public static ConcertDTO toConcertDto(Concert concert) {

        ConcertDTO concertDTO = new ConcertDTO(
                concert.getId(),
                concert.getTitle(),
                concert.getImageName(),
                concert.getBlurb()
        );

        Set<Performer> performers = concert.getPerformers();
        List<PerformerDTO> dtoPerformers = new ArrayList<>();

        // iterate through the performers and map them all to dto performers
        for (Performer p : performers) {
            dtoPerformers.add(PerformerMapper.toPerformerDTO(p));
        }

        concertDTO.setPerformers(dtoPerformers);

        // map the dates too
        List<LocalDateTime> timesList = new ArrayList<>(concert.getDates());

        concertDTO.setDates(timesList);

        return concertDTO;
    }
}
