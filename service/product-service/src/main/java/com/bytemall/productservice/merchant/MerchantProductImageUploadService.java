package com.bytemall.productservice.merchant;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.google.apps.card.v1.Widget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class MerchantProductImageUploadService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final OSS oss;
    private final String bucket;
    private final String publicBaseUrl;
    private final String keyPrefix;

    public MerchantProductImageUploadService(
            OSS oss,
            @Value("${aliyun.oss.bucket}") String bucket,
            @Value("${aliyun.oss.public-base-url}") String publicBaseUrl,
            @Value("${aliyun.oss.key-prefix}") String keyPrefix
    ) {
        this.oss = oss;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
        this.keyPrefix = keyPrefix;
    }

    public MerchantProductResponses.ImageUploadResult UploadImage(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "图片不能为空，且大小不能超过 5 MiB");
        }

//        file.getInputStream() 从前端上传的 MultipartFile 获取字节流
        try (var input = new BufferedInputStream(file.getInputStream())) {
//            input.mark(12) 在当前位置做个标记，此时是文件开头。参数 12 表示接下来读取最多 12 个字节后，仍可回到这个标记。
            input.mark(12);
//            detectType(...)根据这些字节识别 JPEG、PNG 或 WebP，返回包含扩展名和 MIME 类型的 ImageType
//           readNBytes 读取开头最多 12 个字节，文件不足 12 字节时就读取实际剩余的字节。
            ImageType type = detectType(input.readNBytes(12));
//            input.reset() 把读取位置恢复到标记处。少了这行，下面的 putObject 就会从已读过的文件头之后开始读取，上传的数据会缺少开头的字节。
            input.reset();

//            String key = ... 生成图片在 Bucket 里的对象名。比如 keyPrefix 是 products/，识别结果是 PNG，那么 key 就是 products/<随机 UUID>.png。
            String key = keyPrefix + UUID.randomUUID() + "." + type.extension();
//            new ObjectMetadata() 创建一份对象元数据，用来描述上传的图片。
            ObjectMetadata metadata = new ObjectMetadata();
//            setContentLength(file.getSize()) 设置整张图片的大小，单位是字节。
            metadata.setContentLength(file.getSize());
//            setContentType(type.contentType()) 设置图片的 MIME 类型，例如 image/png。浏览器通过这个响应头识别资源类型。
            metadata.setContentType(type.contentType());

//            oss.putObject(...) 把 input 里的完整图片写到指定的 bucket，对象名是 key，并附上刚才设置的 metadata。
            oss.putObject(bucket, key, input, metadata);
            return new MerchantProductResponses.ImageUploadResult(publicBaseUrl + "/" + key);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "读取上传文件失败", e);
        } catch (OSSException | ClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "图片存储服务写入失败", e);
        }
    }

    private static ImageType detectType(byte[] header) {
        String hex = HexFormat.of().formatHex(header);
        if (hex.startsWith("ffd8ff")) {
            return new ImageType("jpg", "image/jpeg");
        }
        if (hex.startsWith("89504e470d0a1a0a")) {
            return new ImageType("png", "image/png");
        }
        if (header.length == 12
                && hex.startsWith("52494646")
                && hex.endsWith("57454250")) {
            return new ImageType("webp", "image/webp");
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "仅支持 JPEG、PNG 或 WebP 图片");
    }

    private record ImageType(String extension, String contentType) {
    }
}
