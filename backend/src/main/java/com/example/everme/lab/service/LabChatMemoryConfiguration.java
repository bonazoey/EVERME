package com.example.everme.lab.service;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** JDBC 메시지를 실험 스키마에 연결한다. 영구 이력은 session_messages에 별도로 보존한다. */
@Configuration(proxyBeanMethods = false)
@Profile("clone-lab")
public class LabChatMemoryConfiguration {
  @Bean
  JdbcChatMemoryRepository labChatMemoryRepository(
      JdbcTemplate jdbc, PlatformTransactionManager tx) {
    return JdbcChatMemoryRepository.builder()
        .jdbcTemplate(jdbc)
        .transactionManager(tx)
        .dialect(
            new JdbcChatMemoryRepositoryDialect() {
              public String getSelectMessagesSql() {
                return "SELECT content,type FROM clone_lab.context WHERE conversation_id=? ORDER BY"
                           + " timestamp";
              }

              public String getInsertMessageSql() {
                return "INSERT INTO clone_lab.context(conversation_id,content,type,timestamp)"
                           + " VALUES(?,?,?,?)";
              }

              public String getSelectConversationIdsSql() {
                return "SELECT DISTINCT conversation_id FROM clone_lab.context";
              }

              public String getDeleteMessagesSql() {
                return "DELETE FROM clone_lab.context WHERE conversation_id=?";
              }
            })
        .build();
  }

  @Bean
  ChatMemory labChatMemory(JdbcChatMemoryRepository repository) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(40)
        .build();
  }
}
