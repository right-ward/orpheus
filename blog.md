---
layout: default
title: Devlog
permalink: /blog/
description: "Development notes on Orpheus, Android preservation, archive verification, Shizuku integration, and restoration planning."
---

# Orpheus devlog

Notes from building Orpheus: Android preservation experiments, archive integrity, package collection, Shizuku access, and the work needed to make backup and restoration safer.

The project is evolving in public. The source code and roadmap are the authoritative places to check what currently works and what remains planned; future posts will record the decisions and lessons behind that work.

Read the [repository](https://github.com/right-ward/orpheus), [development roadmap](https://github.com/right-ward/orpheus/blob/main/ROADMAP.md), or [documentation]({{ '/docs/' | relative_url }}). Subscribe with the <a href="{{ '/feed.xml' | relative_url }}">Atom feed</a>.

{% assign blog_posts = site.posts %}
{% if blog_posts.size > 0 %}
<ul>
{% for post in blog_posts %}
  <li>
    <time datetime="{{ post.date | date_to_xmlschema }}">{{ post.date | date: "%B %-d, %Y" }}</time> — <a href="{{ post.url | relative_url }}">{{ post.title | escape }}</a>
    {% if post.excerpt %}
    <p>{{ post.excerpt | strip_html | normalize_whitespace | truncate: 240 | escape }}</p>
    {% endif %}
  </li>
{% endfor %}
</ul>
{% else %}
<p>No devlog entries have been published on this site yet. The README, design documents, and roadmap describe the current implementation and planned work.</p>
{% endif %}
