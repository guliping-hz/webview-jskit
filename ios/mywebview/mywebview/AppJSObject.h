//
//  AppJSObject.h
//  mywebview
//
//  Created by bo yang on 2024/11/8.
//

#ifndef AppJSObject_h
#define AppJSObject_h

#import <Foundation/Foundation.h>
#import <JavaScriptCore/JavaScriptCore.h>

@protocol AppJSObjectDelegate <JSExport>
-(void)scan:(NSString *)message;
@end

@interface AppJSObject : NSObject<AppJSObjectDelegate>
@property(nonatomic,weak) id<AppJSObjectDelegate> delegate;
@end

#endif /* AppJSObject_h */
