package org.me.retrocoder.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Controller for SPA (Single Page Application) routing.
 * Forwards all non-API, non-static routes to index.html for React Router.
 */
@Controller
public class SpaController {

    /**
     * Forward root to index.html.
     */
    @GetMapping("/")
    public String forwardRoot() {
        return "forward:/index.html";
    }

    /**
     * Forward single-segment paths to index.html (excluding api, ws, assets).
     */
    @GetMapping("/{path:^(?!api$|ws$|assets$)[^\\.]*$}")
    public String forwardSingle(@PathVariable String path) {
        return "forward:/index.html";
    }

    /**
     * Forward two-segment paths to index.html (excluding api/*, ws/*).
     */
    @GetMapping("/{path:^(?!api|ws)[^\\.]*$}/{subpath:[^\\.]*}")
    public String forwardDouble(@PathVariable String path, @PathVariable String subpath) {
        return "forward:/index.html";
    }

    /**
     * Forward three-segment paths to index.html (excluding api/*, ws/*).
     */
    @GetMapping("/{path:^(?!api|ws)[^\\.]*$}/{subpath:[^\\.]*}/{extra:[^\\.]*}")
    public String forwardTriple(@PathVariable String path, @PathVariable String subpath, @PathVariable String extra) {
        return "forward:/index.html";
    }
}
