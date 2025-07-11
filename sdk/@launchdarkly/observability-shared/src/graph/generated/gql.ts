/* eslint-disable */
import * as types from './graphql';



/**
 * Map of all GraphQL operations in the project.
 *
 * This map has several performance disadvantages:
 * 1. It is not tree-shakeable, so it will include all operations in the project.
 * 2. It is not minifiable, so the string of a GraphQL query will be multiple times inside the bundle.
 * 3. It does not support dead code elimination, so it will add unused operations.
 *
 * Therefore it is highly recommended to use the babel or swc plugin for production.
 * Learn more about it here: https://the-guild.dev/graphql/codegen/plugins/presets/preset-client#reducing-bundle-size
 */
type Documents = {
    "fragment MatchParts on MatchConfig {\n  regexValue\n  matchValue\n}\n\nquery GetSamplingConfig($organization_verbose_id: String!) {\n  sampling(organization_verbose_id: $organization_verbose_id) {\n    spans {\n      name {\n        ...MatchParts\n      }\n      attributes {\n        key {\n          ...MatchParts\n        }\n        attribute {\n          ...MatchParts\n        }\n      }\n      events {\n        name {\n          ...MatchParts\n        }\n        attributes {\n          key {\n            ...MatchParts\n          }\n          attribute {\n            ...MatchParts\n          }\n        }\n      }\n      samplingRatio\n    }\n    logs {\n      message {\n        ...MatchParts\n      }\n      severityText {\n        ...MatchParts\n      }\n      attributes {\n        key {\n          ...MatchParts\n        }\n        attribute {\n          ...MatchParts\n        }\n      }\n      samplingRatio\n    }\n  }\n}": typeof types.MatchPartsFragmentDoc,
};
const documents: Documents = {
    "fragment MatchParts on MatchConfig {\n  regexValue\n  matchValue\n}\n\nquery GetSamplingConfig($organization_verbose_id: String!) {\n  sampling(organization_verbose_id: $organization_verbose_id) {\n    spans {\n      name {\n        ...MatchParts\n      }\n      attributes {\n        key {\n          ...MatchParts\n        }\n        attribute {\n          ...MatchParts\n        }\n      }\n      events {\n        name {\n          ...MatchParts\n        }\n        attributes {\n          key {\n            ...MatchParts\n          }\n          attribute {\n            ...MatchParts\n          }\n        }\n      }\n      samplingRatio\n    }\n    logs {\n      message {\n        ...MatchParts\n      }\n      severityText {\n        ...MatchParts\n      }\n      attributes {\n        key {\n          ...MatchParts\n        }\n        attribute {\n          ...MatchParts\n        }\n      }\n      samplingRatio\n    }\n  }\n}": types.MatchPartsFragmentDoc,
};

/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "fragment MatchParts on MatchConfig {\n  regexValue\n  matchValue\n}\n\nquery GetSamplingConfig($organization_verbose_id: String!) {\n  sampling(organization_verbose_id: $organization_verbose_id) {\n    spans {\n      name {\n        ...MatchParts\n      }\n      attributes {\n        key {\n          ...MatchParts\n        }\n        attribute {\n          ...MatchParts\n        }\n      }\n      events {\n        name {\n          ...MatchParts\n        }\n        attributes {\n          key {\n            ...MatchParts\n          }\n          attribute {\n            ...MatchParts\n          }\n        }\n      }\n      samplingRatio\n    }\n    logs {\n      message {\n        ...MatchParts\n      }\n      severityText {\n        ...MatchParts\n      }\n      attributes {\n        key {\n          ...MatchParts\n        }\n        attribute {\n          ...MatchParts\n        }\n      }\n      samplingRatio\n    }\n  }\n}"): typeof import('./graphql').MatchPartsFragmentDoc;


export function graphql(source: string) {
  return (documents as any)[source] ?? {};
}
