package com.jakduk.core.model.embedded;

import com.jakduk.core.common.CommonConst;
import lombok.*;

/**
 * @author <a href="mailto:phjang1983@daum.net">Jang,Pyohwan</a>
 * @company  : http://jakduk.com
 * @date     : 2015. 2. 3.
 * @desc     :
 */

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GalleryStatus {
	
	private CommonConst.GALLERY_STATUS_TYPE status;

	private CommonConst.GALLERY_FROM_TYPE from;
}
