//
//  ViewController.h
//  mywebview
//
//  Created by bo yang on 2024/11/8.
//

#import <UIKit/UIKit.h>
#import <WebKit/Webkit.h>

@interface ViewController : UIViewController<WKScriptMessageHandler,WKUIDelegate,WKNavigationDelegate>

- (WKWebView*)webView;

@end

