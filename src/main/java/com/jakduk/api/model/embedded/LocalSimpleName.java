package com.jakduk.api.model.embedded;

import lombok.*;

/**
 * @author pyohwan
 * 15. 12. 26 오후 10:22
 */

@AllArgsConstructor
@Getter
@ToString
public class LocalSimpleName {

    private String language;
    private String name;

}
