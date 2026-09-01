package com.saarthi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Web View Controller ensuring seamless routing to the 3D WebGL HUD at root URL.
 */
@Controller
public class WebViewController {

    @GetMapping({"/", "/index", "/hud"})
    public String index() {
        return "forward:/index.html";
    }
}
