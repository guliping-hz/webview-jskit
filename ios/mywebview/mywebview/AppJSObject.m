//
//  AppJSObject.m
//  mywebview
//
//  Created by bo yang on 2024/11/8.
//
#import "AppJSObject.h"

@implementation AppJSObject
-(void)scan:(NSString *)message{
    [self.delegate scan:message];
}
@end
