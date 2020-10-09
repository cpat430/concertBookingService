package se325.assignment01.concert.service.mapper;

import se325.assignment01.concert.common.dto.PerformerDTO;
import se325.assignment01.concert.service.domain.Performer;

public class PerformerMapper {

    public static PerformerDTO toPerformerDTO(Performer performer) {

        PerformerDTO dtoPerformer = new PerformerDTO(
                // Long id, String name, String imageName, Genre genre, String blurb
                performer.getId(),
                performer.getName(),
                performer.getImageName(),
                performer.getGenre(),
                performer.getBlurb()
        );

        return dtoPerformer;
    }
}
