package com.smartmall.agent;

public final class Intents {

    public static final String SEARCH = "search_product";
    public static final String COMPARE = "compare";
    public static final String DETAIL = "ask_detail";
    public static final String ORDER = "query_order";
    public static final String ADD_TO_CART = "add_to_cart";
    public static final String CHECKOUT = "checkout";
    public static final String CHITCHAT = "chitchat";
    public static final String CLARIFY = "clarify";
    /** 场景搭配：用户描述一个场景（如海边度假）与预算，由 Agent 组合整套商品 */
    public static final String SCENE_PLAN = "scene_plan";

    private Intents() {
    }

    public static String label(String intent) {
        return switch (intent == null ? "" : intent) {
            case SEARCH -> "商品检索";
            case COMPARE -> "商品对比";
            case DETAIL -> "商品详情";
            case ORDER -> "订单查询";
            case ADD_TO_CART -> "加入购物车";
            case CHECKOUT -> "协助下单";
            case CHITCHAT -> "闲聊";
            case CLARIFY -> "澄清需求";
            case SCENE_PLAN -> "场景搭配";
            default -> intent;
        };
    }

    public static boolean valid(String intent) {
        return SEARCH.equals(intent) || COMPARE.equals(intent) || DETAIL.equals(intent)
                || ORDER.equals(intent) || ADD_TO_CART.equals(intent) || CHECKOUT.equals(intent)
                || CHITCHAT.equals(intent) || CLARIFY.equals(intent)
                || SCENE_PLAN.equals(intent);
    }
}
