package com.mydata.documents;

import com.mydata.auth.Permission;
import com.mydata.common.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "document_acl_entries")
@AttributeOverride(name = "createdAt", column = @Column(name = "synced_at", nullable = false, updatable = false))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentAclEntryEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private ExternalDocumentEntity document;

    @Column(name = "principal_key", nullable = false, columnDefinition = "text")
    private String principalKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private Permission permission;

    @Column(nullable = false, columnDefinition = "text")
    private String source;

    @Column(nullable = false)
    private boolean inherited;

    public static DocumentAclEntryEntity read(
        ExternalDocumentEntity document,
        String principalKey,
        String source,
        boolean inherited
    ) {
        DocumentAclEntryEntity acl = new DocumentAclEntryEntity();
        acl.document = document;
        acl.principalKey = principalKey;
        acl.permission = Permission.READ;
        acl.source = source;
        acl.inherited = inherited;
        return acl;
    }
}
