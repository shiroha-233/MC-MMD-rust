//! 将 Rust `log` 记录缓冲后交给 JNI/Log4j 输出。

use log::{LevelFilter, Log, Metadata, Record};
use once_cell::sync::Lazy;
use std::collections::VecDeque;
use std::sync::{Mutex, Once};

const MAX_BUFFERED_RECORDS: usize = 1024;
const FIELD_SEPARATOR: char = '\t';

static LOGGER: JniBufferedLogger = JniBufferedLogger;
static LOG_BUFFER: Lazy<Mutex<VecDeque<String>>> =
    Lazy::new(|| Mutex::new(VecDeque::with_capacity(MAX_BUFFERED_RECORDS)));
static INIT_LOGGER: Once = Once::new();

struct JniBufferedLogger;

impl Log for JniBufferedLogger {
    fn enabled(&self, metadata: &Metadata<'_>) -> bool {
        metadata.level() <= log::Level::Info
    }

    fn log(&self, record: &Record<'_>) {
        if !self.enabled(record.metadata()) {
            return;
        }

        let message = record.args().to_string();
        for line in message.lines().filter(|line| !line.is_empty()) {
            push_record(format!(
                "{}{}{}{}{}",
                record.level(),
                FIELD_SEPARATOR,
                sanitize_field(record.target()),
                FIELD_SEPARATOR,
                line
            ));
        }
    }

    fn flush(&self) {}
}

/// JNI 加载路径中只安装一次；若进程已有 logger，则保持已有实现不变。
pub(crate) fn ensure_initialized() {
    INIT_LOGGER.call_once(|| {
        if log::set_logger(&LOGGER).is_ok() {
            log::set_max_level(LevelFilter::Info);
        }
    });
}

/// 一次性排空待转发记录，避免 Java 每条日志执行一次 JNI。
pub(crate) fn take_buffered_logs() -> Option<String> {
    let mut buffer = LOG_BUFFER.lock().unwrap_or_else(|error| error.into_inner());
    if buffer.is_empty() {
        return None;
    }

    let mut output = String::new();
    while let Some(record) = buffer.pop_front() {
        if !output.is_empty() {
            output.push('\n');
        }
        output.push_str(&record);
    }
    Some(output)
}

fn push_record(record: String) {
    let mut buffer = LOG_BUFFER.lock().unwrap_or_else(|error| error.into_inner());
    if buffer.len() == MAX_BUFFERED_RECORDS {
        buffer.pop_front();
    }
    buffer.push_back(record);
}

fn sanitize_field(value: &str) -> String {
    value.replace(['\t', '\r', '\n'], " ")
}

#[cfg(test)]
mod tests {
    use super::{push_record, take_buffered_logs, LOG_BUFFER, MAX_BUFFERED_RECORDS};
    use once_cell::sync::Lazy;
    use std::sync::Mutex;

    static TEST_LOCK: Lazy<Mutex<()>> = Lazy::new(|| Mutex::new(()));

    fn clear_buffer() {
        LOG_BUFFER
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .clear();
    }

    #[test]
    fn take_drains_buffer() {
        let _test_guard = TEST_LOCK.lock().unwrap_or_else(|error| error.into_inner());
        clear_buffer();
        push_record("INFO\ttest\tfirst".to_owned());
        push_record("WARN\ttest\tsecond".to_owned());

        assert_eq!(
            take_buffered_logs().as_deref(),
            Some("INFO\ttest\tfirst\nWARN\ttest\tsecond")
        );
        assert!(take_buffered_logs().is_none());
    }

    #[test]
    fn buffer_discards_oldest_records_at_capacity() {
        let _test_guard = TEST_LOCK.lock().unwrap_or_else(|error| error.into_inner());
        clear_buffer();
        for index in 0..=MAX_BUFFERED_RECORDS {
            push_record(index.to_string());
        }

        let output = take_buffered_logs().expect("buffer should contain records");
        assert!(!output.lines().any(|line| line == "0"));
        assert_eq!(output.lines().count(), MAX_BUFFERED_RECORDS);
        let expected_last = MAX_BUFFERED_RECORDS.to_string();
        assert_eq!(output.lines().last(), Some(expected_last.as_str()));
    }
}
