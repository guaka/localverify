//! Experimental record slice only. No archive, filesystem, network, or case APIs.
use regex::Regex;
use serde_json::Value;
use std::sync::{
    atomic::{AtomicBool, AtomicU64, Ordering},
    Arc,
};
uniffi::setup_scaffolding!();

#[derive(uniffi::Object, Default)]
pub struct Cancellation {
    cancelled: AtomicBool,
    progress: AtomicU64,
}
#[uniffi::export]
impl Cancellation {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }
    pub fn progress_units(&self) -> u64 {
        self.progress.load(Ordering::Relaxed)
    }
    pub fn cancel(&self) {
        self.cancelled.store(true, Ordering::Relaxed);
    }
    pub fn is_cancelled(&self) -> bool {
        self.cancelled.load(Ordering::Relaxed)
    }
}
#[derive(Clone, uniffi::Record)]
pub struct Indicator {
    pub id: String,
    pub kind: String,
    pub value: String,
}
#[derive(uniffi::Record)]
pub struct Parsed {
    pub indicators: Vec<Indicator>,
    pub unsupported: Vec<String>,
    pub error: Option<String>,
}
#[derive(uniffi::Record)]
pub struct Finding {
    pub rule: String,
    pub value: String,
    pub source: String,
    pub record: String,
    pub match_type: String,
    pub timestamp: Option<String>,
    pub excerpt: String,
}
#[derive(uniffi::Record, Default)]
pub struct ScanResult {
    pub findings: Vec<Finding>,
    pub coverage_gaps: Vec<String>,
    pub cancelled: bool,
    pub visited_units: u64,
}
fn kind(k: &str) -> bool {
    matches!(
        k,
        "domain-name:value" | "url:value" | "process:name" | "file:path" | "file:name"
    )
}
fn budget(s: &str, cancel: &Cancellation) -> Result<(), String> {
    let (mut depth, mut tokens, mut quoted, mut escaped) = (0u32, 0u32, false, false);
    for (i, b) in s.bytes().enumerate() {
        if i % 4096 == 0 && cancel.is_cancelled() {
            return Err("cancelled".into());
        }
        if quoted {
            if escaped {
                escaped = false;
            } else if b == b'\\' {
                escaped = true;
            } else if b == b'"' {
                quoted = false;
            }
        } else {
            match b {
                b'"' => {
                    quoted = true;
                    tokens += 1
                }
                b'[' | b'{' => {
                    depth += 1;
                    tokens += 1
                }
                b']' | b'}' => depth = depth.saturating_sub(1),
                b',' => tokens += 1,
                _ => {}
            }
            if depth > 64 || tokens > 200_000 {
                return Err("JSON complexity limit reached".into());
            }
        }
    }
    Ok(())
}
#[uniffi::export]
pub fn parse_bundle(data: Vec<u8>, cancel: Arc<Cancellation>) -> Parsed {
    let mut out = Parsed {
        indicators: vec![],
        unsupported: vec![],
        error: None,
    };
    let result = (|| -> Result<(), String> {
        if data.len() > 5 * 1024 * 1024 {
            return Err("Indicator byte limit".into());
        }
        let s = std::str::from_utf8(&data).map_err(|_| "Invalid UTF-8")?;
        budget(s, &cancel)?;
        let root: Value = serde_json::from_str(s).map_err(|_| "Expected STIX2 bundle")?;
        if root["type"] != "bundle" {
            return Err("Expected STIX2 bundle".into());
        }
        let objects = root["objects"].as_array().ok_or("Expected objects array")?;
        let re = Regex::new(r"^\s*\[\s*([a-z-]+:[a-z]+)\s*=\s*'([^'\\]+)'\s*\]\s*$").unwrap();
        for item in objects {
            if cancel.is_cancelled() {
                return Err("cancelled".into());
            }
            if item["type"] != "indicator" {
                continue;
            }
            let id = item["id"].as_str().unwrap_or("unnamed");
            let p = item["pattern"].as_str().unwrap_or("");
            if id.len() > 1024 || p.len() > 8192 {
                return Err("Indicator metadata limit".into());
            }
            let m = re.captures(p);
            if item["revoked"] == true
                || item.get("valid_until").is_some()
                || item.get("pattern_type").is_some_and(|v| v != "stix")
                || m.is_none()
            {
                out.unsupported.push(id.into());
                continue;
            }
            let m = m.unwrap();
            let k = &m[1];
            let v = &m[2];
            if !kind(k)
                || v.trim().is_empty()
                || v.chars().count() > 2048
                || out.indicators.len() >= 2000
            {
                out.unsupported.push(id.into());
                continue;
            }
            out.indicators.push(Indicator {
                id: id.into(),
                kind: k.into(),
                value: v.into(),
            });
        }
        Ok(())
    })();
    if let Err(e) = result {
        out.error = Some(e);
        out.indicators.clear();
    }
    out
}
fn alias(k: &str) -> Option<&'static str> {
    match k.to_ascii_lowercase().as_str() {
        "procname" | "process" | "processname" | "process_name" | "app_name" => {
            Some("process:name")
        }
        "procpath" | "path" | "executablepath" => Some("file:path"),
        "filename" | "name" => Some("file:name"),
        "domain" | "hostname" | "host" => Some("domain-name:value"),
        "url" | "uri" => Some("url:value"),
        _ => None,
    }
}
fn normalized(v: &str, k: &str) -> String {
    if k == "domain-name:value" {
        v.trim_matches('.').to_lowercase()
    } else {
        v.into()
    }
}
type Record = (String, String, String, Option<String>);
fn collect(
    v: &Value,
    path: &str,
    time: Option<String>,
    rows: &mut Vec<Record>,
    nodes: &mut u64,
    cancel: &Cancellation,
) -> Result<(), String> {
    *nodes += 1;
    if cancel.is_cancelled() {
        return Err("cancelled".into());
    }
    if *nodes > 100_000 || path.len() > 4096 {
        return Err("Structured record limit".into());
    }
    match v {
        Value::Object(o) => {
            let time = o
                .get("timestamp")
                .and_then(Value::as_str)
                .or_else(|| o.get("captureTime").and_then(Value::as_str))
                .map(|s| s.chars().take(256).collect())
                .or(time);
            for (k, v) in o {
                let p = format!("{path}.{k}");
                if p.len() > 4096 {
                    return Err("Structured path limit".into());
                }
                if let (Some(s), Some(kind)) = (v.as_str(), alias(k)) {
                    if s.len() <= 8192 {
                        rows.push((p, kind.into(), s.into(), time.clone()));
                    }
                } else if !v.is_string() {
                    collect(v, &p, time.clone(), rows, nodes, cancel)?;
                }
            }
        }
        Value::Array(a) => {
            for (i, v) in a.iter().enumerate() {
                collect(
                    v,
                    &format!("{path}[{i}]"),
                    time.clone(),
                    rows,
                    nodes,
                    cancel,
                )?;
            }
        }
        _ => {}
    };
    Ok(())
}
fn boundary(c: char, domain: bool) -> bool {
    c.is_ascii_alphanumeric()
        || if domain {
            "_.-".contains(c)
        } else {
            "_./:%?=&-".contains(c)
        }
}
fn matched(line: &str, value: &str, domain: bool) -> bool {
    let l = if domain {
        line.to_ascii_lowercase()
    } else {
        line.into()
    };
    let v = if domain {
        value.to_ascii_lowercase()
    } else {
        value.into()
    };
    l.match_indices(&v).any(|(i, _)| {
        !l[..i]
            .chars()
            .next_back()
            .is_some_and(|c| boundary(c, domain))
            && !l[i + v.len()..]
                .chars()
                .next()
                .is_some_and(|c| boundary(c, domain))
    })
}
#[uniffi::export]
pub fn scan_record(
    data: Vec<u8>,
    source: String,
    indicators: Vec<Indicator>,
    cancel: Arc<Cancellation>,
) -> ScanResult {
    let mut out = ScanResult::default();
    let run = (|| -> Result<(), String> {
        if cancel.is_cancelled() {
            return Err("cancelled".into());
        }
        if data.len() > 16 * 1024 * 1024 {
            return Err("Text byte limit".into());
        }
        if indicators.len() > 10_000
            || indicators.iter().any(|i| {
                i.value.is_empty() || i.value.len() > 8192 || i.id.len() > 1024 || !kind(&i.kind)
            })
        {
            return Err("Indicator limit".into());
        }
        let text = std::str::from_utf8(&data).map_err(|_| "Invalid UTF-8")?;
        let mut line_len = 0;
        let mut lines = 1;
        for (i, b) in data.iter().enumerate() {
            if i % 4096 == 0 && cancel.is_cancelled() {
                return Err("cancelled".into());
            }
            if *b == 10 || *b == 13 {
                lines += 1;
                line_len = 0;
            } else {
                line_len += 1;
            }
            if lines > 500_000 || line_len > 1024 * 1024 {
                return Err("Text line limit".into());
            }
        }
        let mut rows = vec![];
        let mut nodes = 0;
        let structured = (|| -> Result<(), String> {
            let mut decode = |s: &str, path: &str| -> Result<bool, String> {
                if !s.trim_start().starts_with(['{', '[']) {
                    return Ok(false);
                }
                budget(s, &cancel)?;
                if let Ok(v) = serde_json::from_str::<Value>(s) {
                    collect(&v, path, None, &mut rows, &mut nodes, &cancel)?;
                    Ok(true)
                } else {
                    Ok(false)
                }
            };
            if !decode(text, "$")? {
                if let Some((a, b)) = text.split_once('\n') {
                    decode(a, "$header")?;
                    decode(b, "$body")?;
                }
            }
            Ok(())
        })();
        if let Err(e) = structured {
            if e == "cancelled" {
                return Err(e);
            }
            rows.clear();
            out.coverage_gaps.push(e);
        }
        let mut work = 0usize;
        for indicator in indicators {
            if cancel.is_cancelled() {
                return Err("cancelled".into());
            }
            out.visited_units += 1;
            cancel.progress.store(out.visited_units, Ordering::Relaxed);
            let selected: Vec<_> = rows
                .iter()
                .filter(|r| {
                    r.1 == indicator.kind
                        && normalized(&r.2, &r.1) == normalized(&indicator.value, &r.1)
                })
                .collect();
            let mut append = |record: String,
                              excerpt: String,
                              timestamp: Option<String>,
                              match_type: &str|
             -> Result<(), String> {
                if cancel.is_cancelled() {
                    return Err("cancelled".into());
                }
                if out.findings.len() >= 10_000 {
                    return Err("Finding limit".into());
                }
                out.findings.push(Finding {
                    rule: indicator.id.clone(),
                    value: indicator.value.clone(),
                    source: source.clone(),
                    record,
                    excerpt: excerpt.chars().take(600).collect(),
                    timestamp,
                    match_type: match_type.into(),
                });
                Ok(())
            };
            if !selected.is_empty() {
                for r in selected {
                    append(r.0.clone(), r.2.clone(), r.3.clone(), "structured")?;
                }
            } else {
                for (i, line) in text.split('\n').enumerate() {
                    if cancel.is_cancelled() {
                        return Err("cancelled".into());
                    }
                    work += line.len() + 1;
                    if work > 128 * 1024 * 1024 {
                        return Err("Text work limit".into());
                    }
                    if matched(
                        line,
                        &indicator.value,
                        indicator.kind == "domain-name:value",
                    ) {
                        append(format!("line {}", i + 1), line.into(), None, "raw-text")?;
                    }
                }
            }
        }
        Ok(())
    })();
    if let Err(e) = run {
        if e == "cancelled" {
            out.cancelled = true;
        } else {
            out.coverage_gaps.push(e);
        }
    }
    out
}
