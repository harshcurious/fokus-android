#!/usr/bin/env -S uv run
# /// script
# dependencies = [
#   "markdown",
# ]
# ///

"""Convert Markdown with Mermaid diagrams to interactive, resizable HTML."""

import argparse
import html
import re
import sys
from pathlib import Path
import markdown

HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>{title}</title>
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.5.1/github-markdown.min.css">
  <script src="https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js"></script>
  <style>
    body {{
      box-sizing: border-box;
      min-width: 200px;
      max-width: 1080px;
      margin: 0 auto;
      padding: 40px 20px;
    }}
    .mermaid-card {{
      position: relative;
      width: 100%;
      height: 550px;
      border: 1px solid #30363d;
      border-radius: 8px;
      background: #0d1117;
      margin: 24px 0;
      overflow: hidden;
      resize: vertical;
    }}
    .mermaid-card .mermaid-stage {{
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
    }}
    .mermaid-card svg {{
      width: 100% !important;
      height: 100% !important;
      max-width: none !important;
    }}
    .mermaid-toolbar {{
      position: absolute;
      top: 10px;
      right: 10px;
      z-index: 10;
      display: flex;
      gap: 6px;
      background: rgba(22, 27, 34, 0.85);
      backdrop-filter: blur(4px);
      padding: 4px;
      border-radius: 6px;
      border: 1px solid #30363d;
    }}
    .mermaid-toolbar button {{
      font-size: 12px;
      line-height: 1;
      padding: 6px 10px;
      background: #21262d;
      color: #c9d1d9;
      border: 1px solid #30363d;
      border-radius: 4px;
      cursor: pointer;
      font-weight: 600;
    }}
    .mermaid-toolbar button:hover {{
      background: #30363d;
      color: #ffffff;
    }}
  </style>
</head>
<body class="markdown-body">
  {content}

  <script type="module">
    import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';

    mermaid.initialize({{
      startOnLoad: false,
      theme: 'dark',
      securityLevel: 'loose',
      flowchart: {{ useMaxWidth: false, htmlLabels: true }}
    }});

    window.addEventListener('DOMContentLoaded', async () => {{
      const stages = document.querySelectorAll('.mermaid-stage');

      for (let i = 0; i < stages.length; i++) {{
        const stage = stages[i];
        const rawCode = stage.getAttribute('data-code');
        const container = stage.closest('.mermaid-card');

        try {{
          const {{ svg }} = await mermaid.render('mermaid-svg-' + i, rawCode);
          stage.innerHTML = svg;

          const svgElement = stage.querySelector('svg');
          if (svgElement) {{
            const panZoom = svgPanZoom(svgElement, {{
              zoomEnabled: true,
              controlIconsEnabled: false,
              fit: true,
              center: true,
              minZoom: 0.1,
              maxZoom: 15
            }});

            const zoomInBtn = container.querySelector('.btn-zoom-in');
            const zoomOutBtn = container.querySelector('.btn-zoom-out');
            const resetBtn = container.querySelector('.btn-reset');

            if (zoomInBtn) zoomInBtn.addEventListener('click', () => panZoom.zoomIn());
            if (zoomOutBtn) zoomOutBtn.addEventListener('click', () => panZoom.zoomOut());
            if (resetBtn) resetBtn.addEventListener('click', () => {{
              panZoom.reset();
              panZoom.fit();
              panZoom.center();
            }});
          }}
        }} catch (err) {{
          stage.innerHTML = `<pre style="color: #ff7b72; padding: 16px;">Failed to render diagram:\\n${{err.message}}</pre>`;
        }}
      }}
    }});
  </script>
</body>
</html>
"""

def extract_and_replace_mermaid(md_text: str) -> tuple[str, dict]:
    """Replace mermaid blocks with placeholders before Markdown parsing."""
    placeholders = {}
    pattern = re.compile(r"```mermaid[ \t]*\r?\n(.*?)\r?\n```", re.DOTALL)

    def replacer(match):
        idx = len(placeholders)
        key = f"<!-- MERMAID-BLOCK-{idx} -->"
        code = match.group(1).strip()
        escaped_attr = html.escape(code, quote=True)
        placeholders[key] = f"""
<div class="mermaid-card">
  <div class="mermaid-toolbar">
    <button class="btn-zoom-in" title="Zoom In">+</button>
    <button class="btn-zoom-out" title="Zoom Out">−</button>
    <button class="btn-reset" title="Reset Fit">Reset</button>
  </div>
  <div class="mermaid-stage" data-code="{escaped_attr}"></div>
</div>
"""
        return key

    processed_md = pattern.sub(replacer, md_text)
    return processed_md, placeholders

def main():
    parser = argparse.ArgumentParser(description="Export Markdown with resizable Mermaid diagrams to HTML.")
    parser.add_argument("input", help="Path to input Markdown file")
    parser.add_argument("-o", "--output", help="Path to output HTML file (defaults to <input>.html)")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.is_file():
        print(f"Error: File '{input_path}' not found.", file=sys.stderr)
        sys.exit(1)

    out_path = Path(args.output) if args.output else input_path.with_suffix(".html")

    raw_md = input_path.read_text(encoding="utf-8")
    preprocessed_md, placeholders = extract_and_replace_mermaid(raw_md)

    html_body = markdown.markdown(
        preprocessed_md,
        extensions=["fenced_code", "tables", "sane_lists"]
    )

    # Restore raw HTML placeholders
    for key, replacement in placeholders.items():
        html_body = html_body.replace(f"<p>{key}</p>", replacement).replace(key, replacement)

    full_html = HTML_TEMPLATE.format(
        title=input_path.stem,
        content=html_body
    )

    out_path.write_text(full_html, encoding="utf-8")
    print(f"Exported: {out_path}")

if __name__ == "__main__":
    main()
