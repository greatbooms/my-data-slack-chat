package com.mydata.connectors.core;

public record RawAclEntry(String principalKey, String permission, boolean inherited, String source) {
}
