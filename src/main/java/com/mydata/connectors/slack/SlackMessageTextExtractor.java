package com.mydata.connectors.slack;

import com.slack.api.model.Attachment;
import com.slack.api.model.Field;
import com.slack.api.model.Message;
import com.slack.api.model.block.ContextBlock;
import com.slack.api.model.block.ContextBlockElement;
import com.slack.api.model.block.HeaderBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.TextObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SlackMessageTextExtractor {
    public String extract(Message message) {
        if (message == null) {
            return null;
        }
        return extract(message.getText(), message.getBlocks(), message.getAttachments());
    }

    public String extract(String text, List<LayoutBlock> blocks, List<Attachment> attachments) {
        Set<String> parts = new LinkedHashSet<>();
        addIfPresent(parts, text);
        addBlockText(parts, blocks);
        if (attachments != null) {
            for (Attachment attachment : attachments) {
                addIfPresent(parts, attachment.getPretext());
                addIfPresent(parts, attachment.getTitle());
                addIfPresent(parts, attachment.getText());
                addFieldText(parts, attachment.getFields());
                addBlockText(parts, attachment.getBlocks());
            }
        }
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    private void addBlockText(Set<String> parts, List<LayoutBlock> blocks) {
        if (blocks == null) {
            return;
        }

        for (LayoutBlock block : blocks) {
            if (block instanceof SectionBlock sectionBlock) {
                addTextObject(parts, sectionBlock.getText());
                if (sectionBlock.getFields() != null) {
                    sectionBlock.getFields().forEach(field -> addTextObject(parts, field));
                }
            } else if (block instanceof HeaderBlock headerBlock) {
                addTextObject(parts, headerBlock.getText());
            } else if (block instanceof ContextBlock contextBlock && contextBlock.getElements() != null) {
                for (ContextBlockElement element : contextBlock.getElements()) {
                    if (element instanceof TextObject textObject) {
                        addTextObject(parts, textObject);
                    }
                }
            }
        }
    }

    private void addFieldText(Set<String> parts, List<Field> fields) {
        if (fields == null) {
            return;
        }

        for (Field field : fields) {
            addIfPresent(parts, field.getTitle());
            addIfPresent(parts, field.getValue());
        }
    }

    private void addTextObject(Set<String> parts, TextObject textObject) {
        if (textObject != null) {
            addIfPresent(parts, textObject.getText());
        }
    }

    private void addIfPresent(Set<String> parts, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            parts.add(normalized);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
