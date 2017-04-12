package com.jakduk.api.restcontroller.board.vo;

import com.jakduk.core.common.CoreConst;
import com.jakduk.api.service.board.vo.GalleryOnBoard;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Size;
import java.util.List;

/**
 * @author pyohwan
 *         16. 7. 16 오후 7:55
 */

@Getter
@ApiModel(value = "글쓰기 & 글편집 폼")
public class FreePostForm {

    @ApiModelProperty(required = true, value = "글 제목", example = "글 제목입니다.")
    @Size(min = 3, max=60)
    @NotEmpty
    private String subject;

    @ApiModelProperty(required = true, value = "글 내용", example = "글 내용입니다.")
    @Size(min = 5)
    @NotEmpty
    private String content;

    @ApiModelProperty(required = true, value = "말머리 코드", example = "FREE")
    private CoreConst.BOARD_CATEGORY_TYPE categoryCode;

    @ApiModelProperty(value = "사진 목록")
    private List<GalleryOnBoard> galleries;
}
