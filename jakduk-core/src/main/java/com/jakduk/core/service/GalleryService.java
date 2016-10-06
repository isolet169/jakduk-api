package com.jakduk.core.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.jakduk.core.authentication.common.CommonPrincipal;
import com.jakduk.core.common.CommonConst;
import com.jakduk.core.dao.JakdukDAO;
import com.jakduk.core.exception.ServiceError;
import com.jakduk.core.exception.ServiceException;
import com.jakduk.core.exception.UserFeelingException;
import com.jakduk.core.model.db.Gallery;
import com.jakduk.core.model.embedded.CommonFeelingUser;
import com.jakduk.core.model.embedded.CommonWriter;
import com.jakduk.core.model.embedded.GalleryStatus;
import com.jakduk.core.model.simple.BoardFreeOnGallery;
import com.jakduk.core.model.simple.GalleryOnList;
import com.jakduk.core.repository.GalleryRepository;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:phjang1983@daum.net">Jang,Pyohwan</a>
 * @company  : http://jakduk.com
 * @date     : 2015. 1. 18.
 * @desc     :
 */

@Slf4j
@Service
public class GalleryService {

	@Value("${storage.image.path}")
	private String storageImagePath;

	@Value("${storage.thumbnail.path}")
	private String storageThumbnailPath;

	@Autowired
	private UserService userService;

	@Autowired
	private GalleryRepository galleryRepository;

	@Autowired
	private JakdukDAO jakdukDAO;

	@Autowired
	private CommonService commonService;

	@Autowired
	private SearchService searchService;

	// 사진 목록.
	public List<GalleryOnList> getGalleriesById(String id, Integer size) {
		if (Objects.nonNull(id))
			return jakdukDAO.findGalleriesById(Direction.DESC, size, new ObjectId(id));
		else
			return jakdukDAO.findGalleriesById(Direction.DESC, size, null);
	}

	// 사진의 좋아요 개수 가져오기.
	public Map<String, Integer> getGalleryUsersLikingCount(List<ObjectId> ids) {
		return jakdukDAO.findGalleryUsersLikingCount(ids);
	}

	// 사진의 싫어요 개수 가져오기.
	public Map<String, Integer> getGalleryUsersDislikingCount(List<ObjectId> ids) {
		return jakdukDAO.findGalleryUsersDislikingCount(ids);
	}

	public Gallery findOneById(String id) {
		return galleryRepository.findOneById(id).orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_GALLERY));
	}

    public List<Gallery> findByIds(List<String> ids) {
        return galleryRepository.findByIdIn(ids);
    }

	/**
	 * 사진 올리기.
	 * @return Gallery 객체
     */
	public Gallery uploadImage(String originalFileName, long size, String contentType, byte[] bytes) {

		Gallery gallery = new Gallery();

		CommonPrincipal principal = userService.getCommonPrincipal();
		CommonWriter writer = new CommonWriter(principal.getId(), principal.getUsername(), principal.getProviderId());
		gallery.setWriter(writer);

		GalleryStatus status = GalleryStatus.builder()
				.status(CommonConst.GALLERY_STATUS_TYPE.TEMP)
				.build();

		gallery.setStatus(status);
		gallery.setFileName(originalFileName);
		gallery.setFileSize(size);
		gallery.setSize(size);

		// 사진 포맷.
		String formatName = "jpg";
		String splitContentType[] = StringUtils.split(contentType, "/");

		if (! splitContentType[1].equals("octet-stream")) {
			formatName = splitContentType[1];
			gallery.setContentType(contentType);
		} else {
			gallery.setContentType("image/jpeg");
		}

		galleryRepository.save(gallery);

		try {
			// 폴더 생성.
			ObjectId objId = new ObjectId(gallery.getId());
			Instant instant = Instant.ofEpochMilli(objId.getDate().getTime());
			LocalDateTime timePoint = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

			Path imageDirPath = Paths.get(storageImagePath, String.valueOf(timePoint.getYear()),
					String.valueOf(timePoint.getMonthValue()), String.valueOf(timePoint.getDayOfMonth()));

			Path thumbDirPath = Paths.get(storageThumbnailPath, String.valueOf(timePoint.getYear()),
					String.valueOf(timePoint.getMonthValue()), String.valueOf(timePoint.getDayOfMonth()));

			if (Files.notExists(imageDirPath, LinkOption.NOFOLLOW_LINKS))
				Files.createDirectories(imageDirPath);

			if (Files.notExists(thumbDirPath, LinkOption.NOFOLLOW_LINKS))
				Files.createDirectories(thumbDirPath);

			// 사진 경로.
			Path imageFilePath = imageDirPath.resolve(gallery.getId() + "." + formatName);
			Path thumbFilePath = thumbDirPath.resolve(gallery.getId() + "." + formatName);

			Integer orientation = 1;
			Integer rotate = 0;

			InputStream exifInputStream = new ByteArrayInputStream(bytes);
			Metadata metadata = ImageMetadataReader.readMetadata(exifInputStream);
			Optional<Directory> directory = Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifIFD0Directory.class));

			if (directory.isPresent())
				orientation = directory.get().getInt(ExifIFD0Directory.TAG_ORIENTATION);

			switch (orientation) {
				case 3: // 180도 회전
					rotate = 180;
					break;
				case 6: // 90도 회전
					rotate = 90;
					break;
				case 8: // 270도 회전
					rotate = 270;
					break;
				default:
					break;
			}

			// 사진 저장.
			if (Files.notExists(imageFilePath, LinkOption.NOFOLLOW_LINKS)) {
				if (("gif".equals(formatName) || CommonConst.GALLERY_MAXIUM_CAPACITY > size) && orientation.equals(1)) {
					Files.write(imageFilePath, bytes);
				} else {
                    double scale = CommonConst.GALLERY_MAXIUM_CAPACITY / (double) size;
                    InputStream originalInputStream = new ByteArrayInputStream(bytes);

					Thumbnails.of(originalInputStream)
							.scale(scale)
							.rotate(rotate)
							.toFile(imageFilePath.toFile());

					BasicFileAttributes attr = Files.readAttributes(imageFilePath, BasicFileAttributes.class);

					gallery.setSize(attr.size());

					galleryRepository.save(gallery);
				}
			}

			// 썸네일 만들기.
			if (Files.notExists(thumbFilePath, LinkOption.NOFOLLOW_LINKS)) {
				InputStream thumbInputStream = new ByteArrayInputStream(bytes);

				Thumbnails.of(thumbInputStream)
						.size(CommonConst.GALLERY_THUMBNAIL_SIZE_WIDTH, CommonConst.GALLERY_THUMBNAIL_SIZE_HEIGHT)
                        .crop(Positions.TOP_CENTER)
						.rotate(rotate)
						.toFile(thumbFilePath.toFile());
			}

		} catch (IOException | MetadataException | ImageProcessingException e) {
			throw new ServiceException(ServiceError.GALLERY_IO_ERROR, e);
		}

		log.debug("gallery=" + gallery);

		return gallery;

	}

	// 이미지 가져오기.
	public ByteArrayOutputStream getGalleryOutStream(Gallery gallery, CommonConst.IMAGE_TYPE imageType) {

		ObjectId objId = new ObjectId(gallery.getId());
		Instant instant = Instant.ofEpochMilli(objId.getDate().getTime());
		LocalDateTime timePoint = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

		String imagePath = null;

		switch (imageType) {
			case FULL:
				imagePath = storageImagePath;
				break;
			case THUMBNAIL:
				imagePath = storageThumbnailPath;
				break;
		}

		String formatName = StringUtils.split(gallery.getContentType(), "/")[1];

		Path filePath = Paths.get(imagePath, String.valueOf(timePoint.getYear()), String.valueOf(timePoint.getMonthValue()),
				String.valueOf(timePoint.getDayOfMonth()), gallery.getId() + "." + formatName);

		if (Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
			try {
				BufferedInputStream in = new BufferedInputStream(new FileInputStream(filePath.toString()));
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream(512);

				int imageByte;

				while ((imageByte = in.read()) != -1){
					byteStream.write(imageByte);
				}

				in.close();
				return byteStream;

			} catch (IOException e) {
				throw new ServiceException(ServiceError.GALLERY_IO_ERROR, e);
			}
		} else {
			throw new ServiceException(ServiceError.NOT_FOUND_GALLERY);
		}
	}

	/**
	 * 사진 삭제.
	 */
	public void removeImage(String id) {

		CommonPrincipal principal = userService.getCommonPrincipal();
		String accountId = principal.getId();

		Gallery gallery = galleryRepository.findOneById(id)
                .orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_GALLERY));

		if (ObjectUtils.isEmpty(gallery.getWriter()))
			throw new ServiceException(ServiceError.UNAUTHORIZED_ACCESS);

		if (! accountId.equals(gallery.getWriter().getUserId()))
            throw new ServiceException(ServiceError.FORBIDDEN);

		ObjectId objId = new ObjectId(gallery.getId());
		Instant instant = Instant.ofEpochMilli(objId.getDate().getTime());
		LocalDateTime timePoint = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

        String formatName = StringUtils.split(gallery.getContentType(), "/")[1];

		Path imageFilePath = Paths.get(storageImagePath, String.valueOf(timePoint.getYear()), String.valueOf(timePoint.getMonthValue()),
                String.valueOf(timePoint.getDayOfMonth()), gallery.getId() + "." + formatName);

		Path thumbThumbnailPath = Paths.get(storageThumbnailPath, String.valueOf(timePoint.getYear()), String.valueOf(timePoint.getMonthValue()),
                String.valueOf(timePoint.getDayOfMonth()), gallery.getId() + "." + formatName);

        // TODO 사진을 삭제하기 전에, 이 사진과 연동된 글이 있는지 검사를 해야 한다. 최종적으로 연동된 글이 전부 없어진다면 사진은 삭제되어야 한다.
		if (Files.exists(imageFilePath, LinkOption.NOFOLLOW_LINKS) && Files.exists(thumbThumbnailPath, LinkOption.NOFOLLOW_LINKS)) {
			try {
				Files.delete(imageFilePath);
				Files.delete(thumbThumbnailPath);
				galleryRepository.delete(gallery);

			} catch (IOException e) {
				throw new ServiceException(ServiceError.GALLERY_IO_ERROR);
			}
		} else {
            throw new ServiceException(ServiceError.NOT_FOUND_GALLERY_FILE);
        }

		// 엘라스틱 서치 document 삭제.
		searchService.deleteDocumentGallery(gallery.getId());
	}

	public Map<String, Object> getGallery(String id, Boolean isAddCookie) {

		Gallery gallery = galleryRepository.findOne(id);

		if (gallery == null) {
			return null;
		}

		if (isAddCookie) {
			int views = gallery.getViews();
			gallery.setViews(++views);
			galleryRepository.save(gallery);
		}

		Map<String, Date> createDate = new HashMap<>();

		Gallery prevGall = jakdukDAO.getGalleryById(new ObjectId(id), Sort.Direction.ASC);
		Gallery nextGall = jakdukDAO.getGalleryById(new ObjectId(id), Sort.Direction.DESC);
		List<BoardFreeOnGallery> posts = jakdukDAO.getBoardFreeOnGallery(new ObjectId(id));

		ObjectId objId = new ObjectId(id);
		createDate.put(id, objId.getDate());

		for (BoardFreeOnGallery post : posts) {
			objId = new ObjectId(post.getId());
			createDate.put(post.getId(), objId.getDate());
		}

		Map<String, Object> data = new HashMap<>();
		data.put("gallery", gallery);
		data.put("prev", prevGall);
		data.put("next", nextGall);
		data.put("linkedPosts", posts);
		data.put("createDate", createDate);

		return data;
	}

	public Map<String, Object> setUserFeeling(String id, CommonConst.FEELING_TYPE feeling) {

		String errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE;

		CommonPrincipal principal = userService.getCommonPrincipal();
		String accountId = principal.getId();
		String accountName = principal.getUsername();

		Gallery gallery = galleryRepository.findOne(id);
		CommonWriter writer = gallery.getWriter();

		List<CommonFeelingUser> usersLiking = gallery.getUsersLiking();
		List<CommonFeelingUser> usersDisliking = gallery.getUsersDisliking();

		if (usersLiking == null) {
			usersLiking = new ArrayList<>();
		}

		if (usersDisliking == null) {
			usersDisliking = new ArrayList<>();
		}

		if (accountId != null && accountName != null) {
			if (writer != null && accountId.equals(writer.getUserId())) {
				errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_WRITER;
			}

			if (errCode.equals(CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE)) {
				Stream<CommonFeelingUser> users = usersLiking.stream();
				Long itemCount = users.filter(item -> item.getUserId().equals(accountId)).count();
				if (itemCount > 0) {
					errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_ALREADY;
				}
			}

			if (errCode.equals(CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE)) {
				Stream<CommonFeelingUser> users = usersDisliking.stream();
				Long itemCount = users.filter(item -> item.getUserId().equals(accountId)).count();
				if (itemCount > 0) {
					errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_ALREADY;
				}
			}

			if (errCode.equals(CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE)) {
				CommonFeelingUser feelingUser = new CommonFeelingUser(new ObjectId().toString(), accountId, accountName);

				if (CommonConst.FEELING_TYPE.LIKE.equals(feeling)) {
					usersLiking.add(feelingUser);
					gallery.setUsersLiking(usersLiking);
				} else {
					usersDisliking.add(feelingUser);
					gallery.setUsersDisliking(usersDisliking);
				}

				galleryRepository.save(gallery);
			} else {
				throw new UserFeelingException(
					CommonConst.USER_FEELING_ERROR_CODE.ALREADY.toString(),
					commonService.getResourceBundleMessage("messages.exception", "exception.select.already.like")
				);
			}
		} else {
			throw new ServiceException(ServiceError.FORBIDDEN);
		}

		Map<String, Object> data = new HashMap<>();
		data.put("feeling", feeling);
		data.put("numberOfLike", usersLiking.size());
		data.put("numberOfDislike", usersDisliking.size());
		return data;
	}

}