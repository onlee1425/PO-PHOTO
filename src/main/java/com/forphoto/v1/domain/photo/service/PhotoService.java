package com.forphoto.v1.domain.photo.service;

import com.forphoto.v1.common.Constants;
import com.forphoto.v1.domain.album.entity.Album;
import com.forphoto.v1.domain.album.repository.AlbumRepository;
import com.forphoto.v1.domain.photo.dto.DeletePhotoResponse;
import com.forphoto.v1.domain.photo.dto.PhotoDto;
import com.forphoto.v1.domain.photo.entity.Photo;
import com.forphoto.v1.domain.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.persistence.EntityNotFoundException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;

    public PhotoDto savePhoto(MultipartFile file, Long albumId) {
        Optional<Album> res = albumRepository.findById(albumId);

        if (res.isEmpty()) {
            throw new EntityNotFoundException("앨범이 존재하지 않습니다");
        }

        String fileName = file.getOriginalFilename();
        int fileSize = (int) file.getSize();
        fileName = checkFileName(fileName, albumId);
        saveFile(file, albumId, fileName);

        Photo photo = new Photo();
        photo.setOriginalUrl("/photos/original/" + albumId + "/" + fileName);
        photo.setThumbUrl("/photos/thumb/" + albumId + "/" + fileName);
        photo.setFileName(fileName);
        photo.setFileSize(fileSize);
        photo.setAlbum(res.get());
        photoRepository.save(photo);

        PhotoDto photoDto = new PhotoDto();
        photoDto.setPhotoId(photo.getPhotoId());
        photoDto.setFileName(photo.getFileName());
        photoDto.setFileSize((int) photo.getFileSize());
        photoDto.setOriginalUrl(photo.getOriginalUrl());
        photoDto.setThumbUrl(photo.getThumbUrl());
        photoDto.setUploadedAt(photo.getUploadedAt());
        photoDto.setAlbumId(albumId);

        return photoDto;

    }

    private String checkFileName(String fileName, Long albumId) {
        String fileNameNoExt = StringUtils.stripFilenameExtension(fileName);
        String ext = StringUtils.getFilenameExtension(fileName);

        Optional<Photo> result = photoRepository.findByFileNameAndAlbum_AlbumId(fileName, albumId);

        int count = 2;
        while (result.isPresent()) {
            fileName = String.format("%s (%d).%s", fileNameNoExt, count, ext);
            result = photoRepository.findByFileNameAndAlbum_AlbumId(fileName, albumId);
            count++;
        }
        return fileName;
    }

    private void saveFile(MultipartFile file, Long albumId, String fileName) {
        try {
            String filePath = albumId + "/" + fileName;
            String original_path = Constants.PATH_PREFIX + "/photos/original";
            Path originalPath = Paths.get(original_path + "/" + albumId);
            String thumb_path = Constants.PATH_PREFIX + "/photos/thumb";
            Path thumbPath = Paths.get(thumb_path + "/" + albumId);
            Files.createDirectories(originalPath);
            Files.createDirectories(thumbPath);

            Files.copy(file.getInputStream(), Paths.get(original_path + "/" + filePath));

            BufferedImage thumbImg = Scalr.resize(ImageIO.read(file.getInputStream()), Constants.THUMB_SIZE, Constants.THUMB_SIZE);
            File thumbFile = new File(thumb_path + "/" + filePath);
            String ext = StringUtils.getFilenameExtension(fileName);
            ImageIO.write(thumbImg, Objects.requireNonNull(ext), thumbFile);

        } catch (Exception e) {
            throw new RuntimeException("Could not store the file. Error : " + e.getMessage());
        }
    }

    //todo : 이미지 파일 검증 기능

    public PhotoDto getPhotoInfo(Long photoId) {
        Optional<Photo> result = photoRepository.findById(photoId);

        if (result.isPresent()) {
            Photo photo = result.get();
            PhotoDto response = new PhotoDto();
            response.setPhotoId(photo.getPhotoId());
            response.setAlbumId(photo.getAlbum().getAlbumId());
            response.setFileName(photo.getFileName());
            response.setOriginalUrl(photo.getOriginalUrl());
            response.setThumbUrl(photo.getThumbUrl());
            response.setUploadedAt(photo.getUploadedAt());
            response.setFileSize((int) photo.getFileSize());

            return response;
        } else {
            throw new RuntimeException("조회되는 사진이 없습니다.");
        }

    }

    public DeletePhotoResponse deletePhoto(Long photoId) {
        Optional<Photo> photoOptional = photoRepository.findById(photoId);

        if (photoOptional.isEmpty()) {
            throw new RuntimeException("해당하는 사진이 없습니다.");
        }

        Photo photo = photoOptional.get();

        String albumId = String.valueOf(photo.getAlbum().getAlbumId());
        String fileName = photo.getFileName();
        String originFilePath = Constants.PATH_PREFIX + "/photos/original/" + albumId + "/" + fileName;
        String thumbFilePath = Constants.PATH_PREFIX + "/photos/thumb/" + albumId + "/" + fileName;

        try {
            Files.deleteIfExists(Paths.get(originFilePath));
            Files.deleteIfExists(Paths.get(thumbFilePath));
        } catch (IOException e) {
            throw new RuntimeException("파일 삭제 중 오류가 발생했습니다.");
        }

        DeletePhotoResponse response = new DeletePhotoResponse();
        response.setPhotoId(photo.getPhotoId());
        response.setFileName(photo.getFileName());
        response.setThumbUrl(photo.getThumbUrl());
        response.setUploadedAt(photo.getUploadedAt());

        photoRepository.delete(photo);

        return response;
    }
}