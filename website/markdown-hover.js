const blockSelector = 'h1, h2, h3, h4, h5, h6, p, li, blockquote, pre';
const standaloneTextSelector = '.small-label, .site-footer > span:first-child, .syntax-demo-file, .syntax-demo-hint';
const markdownTargets = [...document.querySelectorAll(`${blockSelector}, ${standaloneTextSelector}`)]
  .filter((element) => !element.closest('.markdown-hover-ignore'))
  .filter((element) => !element.parentElement?.closest(`${blockSelector}, ${standaloneTextSelector}`))
  .filter((element) => element.textContent.trim());

function markdownInlineContent(node) {
  if (node.nodeType === Node.TEXT_NODE) return node.nodeValue;
  if (node.nodeType !== Node.ELEMENT_NODE) return '';

  const content = [...node.childNodes].map(markdownInlineContent).join('');
  const tag = node.tagName.toLowerCase();

  if (tag === 'strong' || tag === 'b') return `**${content}**`;
  if (tag === 'em' || tag === 'i') return `*${content}*`;
  if (tag === 'del' || tag === 's') return `~~${content}~~`;
  if (tag === 'code') return `\`${content}\``;
  if (tag === 'a') return `[${content}](${node.getAttribute('href') || ''})`;
  if (tag === 'br') return '\n';
  return content;
}

function markdownSourceFor(element) {
  if (element.dataset.markdownSource) return element.dataset.markdownSource;

  const content = markdownInlineContent(element);
  const tag = element.tagName.toLowerCase();
  if (/^h[1-6]$/.test(tag)) return `${'#'.repeat(Number(tag[1]))} ${content}`;
  if (tag === 'li') return `- ${content}`;
  if (tag === 'blockquote') return content.split('\n').map((line) => `> ${line}`).join('\n');
  return content;
}

function setMarkdownState(element, showingMarkdown) {
  if (showingMarkdown) {
    if (!element.dataset.renderedMarkup) {
      element.dataset.renderedMarkup = element.innerHTML;
    }
    element.textContent = element.dataset.markdownSource;
    element.classList.add('is-showing-markdown');
    element.setAttribute('aria-label', `Markdown source: ${element.dataset.markdownSource}`);
  } else {
    element.innerHTML = element.dataset.renderedMarkup;
    element.classList.remove('is-showing-markdown');
    element.removeAttribute('aria-label');
  }
}

markdownTargets.forEach((element) => {
  element.classList.add('markdown-hover-target');
  element.dataset.markdownSource = markdownSourceFor(element);
  if (!element.hasAttribute('tabindex') && element.tagName !== 'A' && element.tagName !== 'BUTTON') {
    element.setAttribute('tabindex', '0');
  }

  element.addEventListener('pointerover', (event) => {
    if (event.target.closest('a, button')) {
      setMarkdownState(element, false);
    } else if (!element.contains(event.relatedTarget) || event.relatedTarget?.closest?.('a, button')) {
      setMarkdownState(element, true);
    }
  });
  element.addEventListener('pointerout', (event) => {
    if (!element.contains(event.relatedTarget)) setMarkdownState(element, false);
  });
  element.addEventListener('focus', () => setMarkdownState(element, true));
  element.addEventListener('blur', () => setMarkdownState(element, false));
  element.addEventListener('click', () => {
    if (window.matchMedia('(hover: none)').matches && !element.querySelector('a, button')) {
      setMarkdownState(element, !element.classList.contains('is-showing-markdown'));
    }
  });
});
