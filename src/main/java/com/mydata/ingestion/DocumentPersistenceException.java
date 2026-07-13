package com.mydata.ingestion;

final class DocumentPersistenceException extends RuntimeException {
    DocumentPersistenceException(RuntimeException cause) {
        super(cause);
    }
}
