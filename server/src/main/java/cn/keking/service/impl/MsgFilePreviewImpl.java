package cn.keking.service.impl;

import cn.keking.model.FileAttribute;
import cn.keking.service.FilePreview;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

/**
 * Dcm 文件处理
 */
@Service
public class MsgFilePreviewImpl implements FilePreview {

    private final CommonPreviewImpl commonPreview;

    public MsgFilePreviewImpl(CommonPreviewImpl commonPreview) {
        this.commonPreview = commonPreview;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        commonPreview.filePreviewHandle(url,model,fileAttribute);
        return MSG_FILE_PREVIEW_PAGE;
    }
}
