-- QQ音乐 鼠标驱动 GUI for CC:Tweaked 高级电脑
-- API: http://8.148.15.185:3300/
-- 登录 Cookie 已预存在后端 (ownCookie=0)；此脚本不处理登录。

local BASE = "http://8.148.15.185:3300"
local DIR = "/" .. fs.getDir(shell.getRunningProgram())
local USER_FILE = DIR .. "/qq_user.cfg"
local SPEAKER = DIR .. "/speakerlib.lua"
local PAGE_SIZE = 10
local QUALITY = "320"
local running = true
local view = "首页"
local stack = {}
local rows = {}
local hit = {}
local page = 1
local pageCount = 1
local scroll = 0
local status = "就绪"
local currentUrl = nil
local config = { qq = "1364546652" }
local playerId = nil
local playerState = {}
local urlCache = {}
local dataCache = {}
local lastPlaylistId, lastTopId, lastSearchKey = nil, nil, nil

-- ===== 工具函数 =====

local function clip(s, n)
    s = tostring(s or "")
    if n <= 0 then return "" end
    local charCount = utf8 and utf8.len and utf8.len(s) or #s
    if charCount <= n then return s end
    if n <= 3 then return utf8 and utf8.sub and utf8.sub(s, 1, n) or s:sub(1, n) end
    return (utf8 and utf8.sub and utf8.sub(s, 1, n - 3) or s:sub(1, n - 3)) .. "..."
end

local function colour(c, fallback)
    if term.isColor and term.isColor() then return c end
    return fallback or colors.white
end

local function setLine(y, text, fg, bg)
    local w = select(1, term.getSize())
    term.setCursorPos(1, y)
    term.setTextColor(colour(fg, colors.white))
    term.setBackgroundColor(colour(bg, colors.black))
    term.write(clip(text, w) .. string.rep(" ", math.max(0, w - #clip(text, w))))
end

local function urlEncode(s)
    s = tostring(s or "")
    return (s:gsub("([^%w%-_%.~])", function(c) return string.format("%%%02X", string.byte(c)) end))
end

local function query(params)
    local out = {}
    for k, v in pairs(params or {}) do
        if v ~= nil and tostring(v) ~= "" then out[#out + 1] = urlEncode(k) .. "=" .. urlEncode(v) end
    end
    table.sort(out)
    return #out > 0 and "?" .. table.concat(out, "&") or ""
end

local function request(path, params, extraHeaders)
    params = params or {}
    local headers = { ["User-Agent"] = "CC:Tweaked QQ音乐 GUI" }
    if extraHeaders then for k, v in pairs(extraHeaders) do headers[k] = v end end
    local response, err = http.get(BASE .. path .. query(params), headers, false)
    if not response then error(tostring(err or "HTTP 请求失败"), 0) end
    local body = response.readAll() or ""
    local code = response.getResponseCode and response.getResponseCode() or 200
    response.close()
    if code < 200 or code >= 300 then error("HTTP " .. tostring(code), 0) end
    local ok, data = pcall(textutils.unserialiseJSON, body)
    if not ok or type(data) ~= "table" then error("响应不是有效的 JSON", 0) end
    if data.result ~= nil and data.result ~= 100 then
        local msg = data.errMsg or data.message or ""
        if data.result == 301 then error("未登录 (301) " .. msg, 0)
        elseif data.result == 200 then error("失败 (200) " .. msg, 0)
        elseif data.result == 400 then error("异常 (400) " .. msg, 0)
        elseif data.result == 500 then error("参数错误 (500) " .. msg, 0)
        else error("错误 (result=" .. tostring(data.result) .. ") " .. msg, 0)
        end
    end
    return data
end

-- ===== 配置 =====

local function readConfig()
    if not fs.exists(USER_FILE) then return end
    local h = fs.open(USER_FILE, "r")
    if not h then return end
    for line in (h.readAll() or ""):gmatch("[^\r\n]+") do
        local k, v = line:match("^%s*([^=]+)%s*=%s*(.*)$")
        if k == "qq" then config.qq = v end
    end
    h.close()
end

local function saveConfig()
    local h = fs.open(USER_FILE, "w")
    if not h then return false end
    h.writeLine("qq=" .. config.qq)
    h.close()
    return true
end

-- ===== 数据提取 =====

local function first(t, keys)
    if type(t) ~= "table" then return nil end
    for i = 1, #keys do if t[keys[i]] ~= nil then return t[keys[i]] end end
end

local function singerName(s)
    if type(s) == "string" then return s end
    if type(s) ~= "table" then return "未知艺术家" end
    local a = {}
    for i = 1, #s do
        a[#a + 1] = type(s[i]) == "table" and (s[i].name or s[i].title or "") or tostring(s[i])
    end
    return #a > 0 and table.concat(a, "/") or "未知艺术家"
end

local function songOf(r)
    if type(r) ~= "table" then return nil end
    local songid = first(r, { "songid", "id", "songId" })
    local songmid = first(r, { "songmid", "mid", "song_mid" })
    if not songid and not songmid then return nil end
    local sec = tonumber(first(r, { "interval", "duration", "dt", "length" })) or 0
    if sec > 10000 then sec = sec / 1000 end
    return {
        id = tostring(songid or songmid),
        mid = tostring(songmid or songid),
        name = tostring(first(r, { "songname", "name", "title" }) or "未命名"),
        singer = singerName(first(r, { "singer", "singers", "artist" })),
        album = tostring(first(r, { "albumname", "album", "album_name", "albumName" }) or ""),
        duration = sec,
        mediaId = (function() local m = first(r, { "strMediaMid", "strMediaId", "mediaId", "media_mid" }); return m and tostring(m) or nil end)(),
        type = "song",
        raw = r,
    }
end

local function songList(value)
    local out = {}
    if type(value) ~= "table" then return out end
    for i = 1, #value do local s = songOf(value[i]); if s then out[#out + 1] = s end end
    return out
end

local function extractSongs(data)
    if type(data) ~= "table" then return {} end
    local candidates = {
        data.list,
        data.songlist,
        data.song and data.song.list,
    }
    for i = 1, #candidates do
        if type(candidates[i]) == "table" then
            local x = songList(candidates[i])
            if #x > 0 then return x end
        end
    end
    return {}
end

local function extractPlaylists(data)
    if type(data) ~= "table" then return {} end
    local list = data.list or data.cdlist or data.mydiss or data.playlist or data.playlists or {}
    if type(list) ~= "table" then return {} end
    local out = {}
    for i = 1, #list do
        local p = list[i]
        if type(p) == "table" then
            local id = first(p, { "dissid", "tid", "id", "dirid" })
            if id then
                out[#out + 1] = {
                    id = tostring(id),
                    name = tostring(first(p, { "dissname", "name", "title", "label" }) or "未命名歌单"),
                    count = tonumber(first(p, { "song_cnt", "songnum", "total_song_num" })) or 0,
                    type = "playlist",
                }
            end
        end
    end
    return out
end

local function withQQ(params)
    params = params or {}
    if config.qq ~= "" then params.id = config.qq end
    return params
end

-- ===== API 函数 =====

local function apiSearch(key, p)
    local k = "search:" .. key .. ":" .. p
    if dataCache[k] then return dataCache[k] end
    local resp = request("/search", { key = key, pageNo = tostring(p), pageSize = tostring(PAGE_SIZE), t = "0" })
    local x = extractSongs(resp.data); dataCache[k] = x; return x
end

local function apiHotSearch()
    if dataCache.hotSearch then return dataCache.hotSearch end
    local resp = request("/search/hot", {})
    local data = resp.data or {}
    local out = {}
    if type(data) == "table" then
        for i = 1, #data do
            local item = data[i]
            if type(item) == "table" then
                local keyword = item.k or item.key or item.name
                if keyword and keyword ~= "" then
                    out[#out + 1] = {
                        name = tostring(keyword) .. (item.n and (" (" .. tostring(item.n) .. ")") or ""),
                        keyword = tostring(keyword),
                        type = "hotkey",
                    }
                end
            end
        end
    end
    dataCache.hotSearch = out
    return out
end

local function apiUserFavorites()
    if dataCache.favorites then return dataCache.favorites end
    if config.qq == "" then
        dataCache.favorites = {}
        return {}
    end
    -- 后端直接将用户 QQ 号映射到收藏歌单 ID，
    -- 所以 /songlist?id=<QQ> 返回"我喜欢"歌单及其完整歌曲列表。
    local resp = request("/songlist", { id = config.qq })
    local data = resp.data or {}
    local songs = type(data.songlist) == "table" and songList(data.songlist) or {}
    dataCache.favorites = songs
    return songs
end

local function apiUserSonglists()
    if dataCache.myPlaylists then return dataCache.myPlaylists end
    local resp = request("/user/songlist", withQQ({}))
    local x = extractPlaylists(resp.data)
    dataCache.myPlaylists = x
    return x
end

local function apiCollectSonglists()
    if dataCache.collectPlaylists then return dataCache.collectPlaylists end
    local resp = request("/user/collect/songlist", withQQ({}))
    local x = extractPlaylists(resp.data)
    dataCache.collectPlaylists = x
    return x
end

local function apiSonglistDetail(id)
    local k = "playlist:" .. id
    if dataCache[k] then return dataCache[k] end
    local resp = request("/songlist", { id = tostring(id) })
    local data = resp.data or {}
    local songs = type(data.songlist) == "table" and songList(data.songlist) or {}
    dataCache[k] = songs
    return songs
end

local function apiRecommendPlaylists()
    if dataCache.recommendPlaylists then return dataCache.recommendPlaylists end
    local resp = request("/recommend/playlist", { pageSize = tostring(PAGE_SIZE), pageNo = "1" })
    local x = extractPlaylists(resp.data)
    dataCache.recommendPlaylists = x
    return x
end

local function apiTopCategory()
    if dataCache.topCategory then return dataCache.topCategory end
    local resp = request("/top/category", { showDetail = "0" })
    local data = resp.data or {}
    local out = {}
    if type(data) == "table" then
        for i = 1, #data do
            local g = data[i]
            if type(g) == "table" then
                local tops = g.list or g.toplist or {}
                for j = 1, #tops do
                    local t = tops[j]
                    if type(t) == "table" then
                        local id = t.topId or t.id
                        if id then
                            out[#out + 1] = {
                                id = tostring(id),
                                name = tostring(t.label or t.name or t.title or "排行榜"),
                                type = "top",
                            }
                        end
                    end
                end
            end
        end
    end
    dataCache.topCategory = out
    return out
end

local function apiTop(id)
    local k = "top:" .. id
    if dataCache[k] then return dataCache[k] end
    local resp = request("/top", { id = tostring(id), pageSize = "100" })
    local data = resp.data or {}
    local songs = type(data.list) == "table" and songList(data.list) or extractSongs(data)
    dataCache[k] = songs
    return songs
end

local function apiNewSongs()
    if dataCache.newSongs then return dataCache.newSongs end
    local resp = request("/new/songs", { type = "0" })
    local x = extractSongs(resp.data)
    dataCache.newSongs = x
    return x
end

local function parseUrl(data)
    if type(data) == "string" and data ~= "" then return data end
    if type(data) == "table" then
        return data.url or data.purl or data.play_url or data.download_url
    end
end

local function getUrl(song)
    local k = song.mid .. ":" .. QUALITY
    if urlCache[k] then return urlCache[k] end

    local ok, resp = pcall(request, "/song/url", { id = song.mid, type = QUALITY })
    if ok and resp then
        local u = parseUrl(resp.data)
        if u and u ~= "" then urlCache[k] = u; return u end
    end

    if not song.mediaId then
        local ok2, resp2 = pcall(request, "/song", { songmid = song.mid })
        if ok2 and resp2 and resp2.data then
            local info = resp2.data.track_info or resp2.data
            song.mediaId = info and (info.strMediaMid or info.strMediaId or info.mediaId or (info.extras and (info.extras.strMediaMid or info.extras.strMediaId)))
            if song.mediaId then song.mediaId = tostring(song.mediaId) end
        end
    end

    if song.mediaId and song.mediaId ~= song.mid then
        local ok3, resp3 = pcall(request, "/song/url", { id = song.mid, type = QUALITY, mediaId = song.mediaId })
        if ok3 and resp3 then
            local u = parseUrl(resp3.data)
            if u and u ~= "" then urlCache[k] = u; return u end
        end
    end

    return nil
end

-- ===== 界面 =====

local function fmt(sec)
    sec = math.floor(tonumber(sec) or 0)
    return string.format("%d:%02d", math.floor(sec / 60), sec % 60)
end

local function clearHit() hit = {} end

local function button(y, x1, x2, label, act, extra)
    local w = select(1, term.getSize())
    x1 = x1 or 1; x2 = x2 or w
    local width = x2 - x1 + 1
    local text = clip(label, width)
    term.setCursorPos(x1, y)
    term.setTextColor(colour(colors.yellow, colors.white))
    term.setBackgroundColor(colour(colors.gray, colors.black))
    term.write(text .. string.rep(" ", math.max(0, width - #text)))
    local h = { x1 = x1, x2 = math.min(w, x2), y1 = y, y2 = y, action = act }
    if extra then for k, v in pairs(extra) do h[k] = v end end
    hit[#hit + 1] = h
end

local function homeMenu()
    return {
        { type = "menu", action = "favorites", name = "我的收藏" },
        { type = "menu", action = "myPlaylists", name = "我的歌单" },
        { type = "menu", action = "recommendPlaylists", name = "推荐歌单" },
        { type = "menu", action = "topCategory", name = "排行榜" },
        { type = "menu", action = "newSongs", name = "新歌速递" },
        { type = "menu", action = "search", name = "搜索歌曲" },
        { type = "menu", action = "hotSearch", name = "热搜关键词" },
        { type = "menu", action = "setQQ", name = "设置 QQ 号 (当前: " .. (config.qq ~= "" and config.qq or "未设置") .. ")" },
    }
end

local function draw()
    local w, h = term.getSize()
    clearHit()
    term.setBackgroundColor(colors.black)
    term.clear()

    local qqText = config.qq ~= "" and ("QQ:" .. config.qq) or "QQ:未设置"
    setLine(1, "QQ音乐 | " .. view .. " | 音质:" .. QUALITY .. " | " .. qqText, colors.white, colors.blue)

    local statusText = status
    if playerState.name then
        local pbar = ""
        if playerState.total and playerState.total > 0 then
            local ratio = math.max(0, math.min(1, (playerState.progress or 0) / playerState.total))
            local barW = 20
            local filled = math.floor(barW * ratio)
            pbar = " [" .. string.rep("=", filled) .. string.rep("-", barW - filled) .. "]"
        end
        statusText = "♪ " .. clip(playerState.name, 24) .. " - " .. (playerState.status or "") .. pbar
    elseif currentUrl then
        statusText = "链接: " .. clip(currentUrl, w - 6)
    end
    setLine(2, statusText, colors.lime, colors.black)

    local listTop = 4
    local listBottom = math.max(4, h - 3)
    local maxRows = listBottom - listTop + 1

    local showRows = view == "首页" and homeMenu() or rows

    if #showRows == 0 then
        setLine(listTop, "(无数据)", colors.gray, colors.black)
    else
        for i = 1, maxRows do
            local index = scroll + i
            local item = showRows[index]
            if item then
                local text
                if item.type == "menu" then
                    text = "  " .. item.name
                elseif item.type == "hotkey" then
                    text = string.format("%02d. %s", index, item.name)
                elseif item.type == "playlist" or item.type == "top" then
                    local cnt = item.count and item.count > 0 and (" (" .. item.count .. " 首)") or ""
                    text = string.format("%02d. %s%s", index, item.name, cnt)
                else
                    text = string.format("%02d. %s - %s  %s", index, item.name, item.singer, item.duration and fmt(item.duration) or "")
                end
                local fg = i % 2 == 0 and colors.lightGray or colors.white
                local bg = i % 2 == 0 and colors.gray or colors.black
                setLine(listTop + i - 1, text, fg, bg)
                hit[#hit + 1] = { x1 = 1, x2 = w, y1 = listTop + i - 1, y2 = listTop + i - 1, action = "row", index = index }
            end
        end
    end

    if view == "首页" then
        setLine(h - 1, "提示: 点击项目,按 Q 退出", colors.lightBlue, colors.black)
        button(h, 1, w, "[退出]", "quit")
    else
        local pageText = "第 " .. page .. " 页"
        if pageCount > 1 then pageText = pageText .. " / " .. pageCount end
        setLine(h - 1, pageText, colors.lightBlue, colors.black)
        button(h, 1, math.floor(w / 4), "[返回]", "back")
        button(h, math.floor(w / 4) + 1, math.floor(2 * w / 4), "[刷新]", "refresh")
        button(h, math.floor(2 * w / 4) + 1, math.floor(3 * w / 4), "[链接]", "url")
        button(h, math.floor(3 * w / 4) + 1, w, "[退出]", "quit")
    end
end

local function showError(e)
    status = "错误: " .. clip(e, 50)
    draw()
    os.sleep(2)
end

local function setQQ()
    term.clear(); term.setCursorPos(1, 2)
    print("设置 QQ 号 (用户 API 必需,公共 API 可选):")
    local qq = read()
    config.qq = (qq and qq:gsub("%s", "")) or ""
    saveConfig()
    status = "QQ 号已更新"
end

local function searchInput()
    term.clear(); term.setCursorPos(1, 2)
    write("搜索关键词: ")
    local key = read()
    if key and key ~= "" then
        lastSearchKey = key; page = 1; dataCache["search:" .. key .. ":1"] = nil
        status = "加载中..."; draw()
        local ok, result = pcall(apiSearch, key, page)
        if not ok then showError(result); return end
        rows = result or {}; view = "search"
        pageCount = math.max(1, math.ceil(#rows / PAGE_SIZE))
        scroll = 0
        status = #rows > 0 and ("找到 " .. #rows .. " 个结果") or "无结果"
    end
end

local function loadView(kind, extra)
    status = "加载中..."; draw()
    local handler = {
        favorites = function() return apiUserFavorites(), "favorites" end,
        myPlaylists = function() return apiUserSonglists(), "myPlaylists" end,
        collectPlaylists = function() return apiCollectSonglists(), "collectPlaylists" end,
        playlist = function() return apiSonglistDetail(extra), "playlist" end,
        recommendPlaylists = function() return apiRecommendPlaylists(), "recommendPlaylists" end,
        topCategory = function() return apiTopCategory(), "topCategory" end,
        top = function() return apiTop(extra), "top" end,
        newSongs = function() return apiNewSongs(), "newSongs" end,
        hotSearch = function() return apiHotSearch(), "hotSearch" end,
    }
    local fn = handler[kind]
    if not fn then return end
    local ok, a, b = pcall(fn)
    if not ok then showError(a); return end
    rows = a or {}
    view = b or kind
    pageCount = math.max(1, math.ceil(#rows / PAGE_SIZE))
    scroll = 0
    status = #rows > 0 and ("已加载 " .. #rows .. " 项") or "无数据"
end

local function play(song)
    status = "正在获取下载链接..."; currentUrl = nil; draw()
    local ok, u = pcall(getUrl, song)
    if not ok or not u then showError(ok and "无可用下载链接 (可能需要登录或 VIP)" or u); return end
    currentUrl = u
    if playerId then os.queueEvent("speakerlib_stop", playerId); os.sleep(0.2) end
    playerId = "qq_" .. tostring(os.epoch("utc"))
    playerState = { name = song.name, status = "准备中", progress = 0, total = song.duration }
    os.queueEvent("qqmusic_play", playerId, u, song.name)
end

local function showFirstUrl()
    if #rows == 0 then status = "无可预览的歌曲"; return end
    local item = rows[scroll + 1]
    if not item or item.type ~= "song" then status = "当前行不是歌曲"; return end
    status = "正在获取下载链接..."; currentUrl = nil; draw()
    local ok, u = pcall(getUrl, item)
    if not ok or not u then showError(ok and "无可用下载链接" or u); return end
    currentUrl = u
    status = "链接已显示在状态栏"
    draw()
    os.sleep(3)
end

local function playerLoop()
    while running do
        local e = { os.pullEventRaw() }
        if e[1] == "qqmusic_play" then
            shell.run(SPEAKER, e[3], "-noui", "-id", e[2])
        end
    end
end

local function handleMenuAction(act)
    if act == "favorites" then loadView("favorites")
    elseif act == "myPlaylists" then loadView("myPlaylists")
    elseif act == "collectPlaylists" then loadView("collectPlaylists")
    elseif act == "recommendPlaylists" then loadView("recommendPlaylists")
    elseif act == "topCategory" then loadView("topCategory")
    elseif act == "newSongs" then loadView("newSongs")
    elseif act == "search" then searchInput()
    elseif act == "hotSearch" then loadView("hotSearch")
    elseif act == "setQQ" then setQQ()
    end
end

local function refreshCurrent()
    dataCache = {}
    if view == "favorites" then loadView("favorites")
    elseif view == "myPlaylists" then loadView("myPlaylists")
    elseif view == "collectPlaylists" then loadView("collectPlaylists")
    elseif view == "playlist" and lastPlaylistId then loadView("playlist", lastPlaylistId)
    elseif view == "search" and lastSearchKey then
        page = 1
        local ok, r = pcall(apiSearch, lastSearchKey, page)
        if ok then rows = r or {}; pageCount = math.max(1, math.ceil(#rows / PAGE_SIZE)); scroll = 0; status = "已刷新" end
    elseif view == "recommendPlaylists" then loadView("recommendPlaylists")
    elseif view == "topCategory" then loadView("topCategory")
    elseif view == "top" and lastTopId then loadView("top", lastTopId)
    elseif view == "newSongs" then loadView("newSongs")
    elseif view == "hotSearch" then loadView("hotSearch")
    end
end

local function action(a, item)
    if a == "quit" then
        running = false
        if playerId then os.queueEvent("speakerlib_stop", playerId) end
    elseif a == "back" then
        if #stack > 0 then view = table.remove(stack) else view = "首页" end
        rows = {}; page = 1; scroll = 0; currentUrl = nil; status = "就绪"
    elseif a == "refresh" then refreshCurrent()
    elseif a == "url" then showFirstUrl()
    elseif a == "row" and item then
        local showRows = view == "首页" and homeMenu() or rows
        local r = showRows[item]
        if not r then return end
        if r.type == "menu" then
            handleMenuAction(r.action)
        elseif r.type == "hotkey" then
            lastSearchKey = r.keyword; page = 1
            status = "加载中..."; draw()
            local ok, result = pcall(apiSearch, r.keyword, page)
            if not ok then showError(result); return end
            rows = result or {}; view = "search"
            pageCount = math.max(1, math.ceil(#rows / PAGE_SIZE))
            scroll = 0
            status = #rows > 0 and ("找到 " .. #rows .. " 个结果") or "无结果"
        elseif r.type == "playlist" then
            lastPlaylistId = r.id
            stack[#stack + 1] = view; loadView("playlist", r.id)
        elseif r.type == "top" then
            lastTopId = r.id; stack[#stack + 1] = view; loadView("top", r.id)
        else
            play(r)
        end
    end
end

local function guiLoop()
    readConfig()
    while running do
        draw()
        local e = { os.pullEventRaw() }
        local name = e[1]
        if name == "mouse_click" then
            for i = #hit, 1, -1 do
                local b = hit[i]
                if e[3] >= b.x1 and e[3] <= b.x2 and e[4] >= b.y1 and e[4] <= b.y2 then
                    action(b.action, b.index); break
                end
            end
        elseif name == "mouse_scroll" and view ~= "首页" then
            local _, h = term.getSize()
            local maxScroll = math.max(0, #rows - (h - 6))
            scroll = math.max(0, math.min(maxScroll, scroll + e[2]))
        elseif name == "key" then
            if e[2] == keys.q or e[2] == keys.escape then
                action(view == "首页" and "quit" or "back")
            elseif e[2] == keys.r then action("refresh")
            elseif e[2] == keys.u then showFirstUrl()
            elseif e[2] == keys.slash then searchInput()
            elseif e[2] == keys.pageUp then scroll = math.max(0, scroll - 5)
            elseif e[2] == keys.pageDown then
                local _, h = term.getSize()
                scroll = math.min(math.max(0, #rows - (h - 6)), scroll + 5)
            end
        elseif name == "speakerlib_state" and e[2] == playerId then
            local ok, s = pcall(textutils.unserialiseJSON, e[3])
            if ok and s then
                playerState.status = s.paused and "已暂停" or (s.open and "播放中" or "已结束")
                playerState.progress = s.progress; playerState.total = s.total
            end
        elseif (name == "speakerlib_play_end" or name == "speakerlib_play_stop") and e[2] == playerId then
            playerState.status = "已停止"
        end
    end
end

parallel.waitForAny(guiLoop, playerLoop)
term.setBackgroundColor(colors.black); term.setTextColor(colors.white); term.clear(); term.setCursorPos(1, 1)
print("QQ音乐已退出")
