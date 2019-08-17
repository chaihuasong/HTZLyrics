# 简介 #
该开源依赖库是一个歌词模块功能，现在把该功能模块独立出来进行优化，并弄成了一个开源依赖库，其它音乐播放器项目只要引用该库并调用接口。
注：其默认歌词格式的编码都是utf-8，使用过程中请注意编码一致的问题，

# 后期优化 #
- 行歌词移动动画。因为用Scroller来做动画，因为其每次动画变化都是整数，所以动画整体看起来不是好流畅
，下次考虑通过Scroller来获取动画的最终距离，通过ObjectAnimator来做动画，生成float随机数，到时看一下动画的效果。

# Gradle #
1.root build.gradle

	`allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}`
	
2.app build.gradle

`dependencies {
	         compile 'com.github.chaihuasong:HPLyrics:v1.0'
	}`


# 混淆注意 #
-keep class com.chs.htz.lyrics.** { *; }

# 部分API #
- setPaintColor：设置默认画笔颜色
- setPaintHLColor：设置高亮画笔颜色
- setExtraLyricsListener：设置额外歌词回调方法，多用于加载歌词完成后，根据额外歌词的状态来判断是否需要显示翻译、音译歌词按钮
- setSearchLyricsListener：无歌词时，搜索歌词接口
- setOnLrcClickListener：多行歌词中歌词快进时，点击播放按钮时，调用。
- setFontSize：设置默认画笔的字体大小，可根据参数来设置是否要刷新view
- setExtraLrcStatus：设置额外歌词状态
- setLyricsReader：设置歌词读取器
- play：设置歌词当前的播放进度（播放歌曲时调用一次即可）
- pause：暂停歌词
- seekto：快进歌词
- resume：唤醒
- initLrcData：初始化歌词内容
- setTranslateDrawLrcColorType：设置翻译歌词绘画颜色类型
- setTranslateDrawType：设置翻译歌词绘画类型