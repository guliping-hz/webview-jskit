//
//  ViewController.m
//  mywebview
//
//  Created by bo yang on 2024/11/8.
//

#import "ViewController.h"

@interface ViewController ()

@end

#define MINI_GAME_URL @"https://minigameurl"

@implementation ViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.

    
    NSURLRequest *request = [NSURLRequest requestWithURL:[NSURL URLWithString: MINI_GAME_URL]];
    [self.webView loadRequest:request];
    [self.view addSubview:self.webView];
}

WKWebView* _webView = NULL;
- (WKWebView*)webView {
    if (!_webView) {
        WKWebViewConfiguration* configuration = [[WKWebViewConfiguration alloc] init];
        
        _webView = [[WKWebView alloc] initWithFrame: self.view.frame configuration: configuration];
        
        WKUserContentController* userCC = _webView.configuration.userContentController;
        
        [userCC addScriptMessageHandler: self
                                   name: @"hideCloseIcon"];
        
        _webView.UIDelegate = self;
        _webView.navigationDelegate = self;
    }
    
    return _webView;
}

#pragma mark - WKScriptMessageHandler
- (void)userContentController:(WKUserContentController *)userContentController didReceiveScriptMessage:(WKScriptMessage *)message{
    if ([message.name isEqualToString:@"hideCloseIcon"]) {
        //调用原生
        NSLog(@"call hideCloseIcon");
     }
}

#pragma mark - WKUIDelegate delegate method
- (void)webView:(WKWebView *)webView runJavaScriptTextInputPanelWithPrompt:(NSString *)prompt defaultText:(NSString *)defaultText initiatedByFrame:(WKFrameInfo *)frame
completionHandler:(void (^)(NSString * _Nullable))completionHandler {
    if (prompt) {
        if ([prompt isEqualToString: @"getGameNeedInfoPrompt"]) {
            completionHandler(@"{\"userId\":1,\"token\":\"xxxtokenyyy\"}");
        }
    }
}

-(void)walletUpdate{
    [self.webView evaluateJavaScript:@"walletUpdate()" completionHandler:^(id _Nullable, NSError * _Nullable error) {
            
    }];
}

@end
